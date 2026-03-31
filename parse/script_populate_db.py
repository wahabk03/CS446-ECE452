import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
from firebase_admin import messaging
import scrape_schedule
import sys

"""
INSTRUCTIONS:

1. Prerequisite: You must have a Firebase project set up.
2. Go to Firebase Console > Project Settings > Service accounts.
3. Click "Generate new private key". This will download a JSON file.
4. Rename that file to 'serviceAccountKey.json' and place it in this directory (parse/).
   (Note: DO NOT commit this key to version control!)
5. Install firebase-admin:
   pip install firebase-admin

6. Run this script:
   python parse/script_populate_db.py

   First run (skip notifications to avoid spam):
   python parse/script_populate_db.py --skip-notify
"""


def initialize_firebase():
    try:
        cred = credentials.Certificate('parse/serviceAccountKey.json')
        firebase_admin.initialize_app(cred)
        return firestore.client()
    except FileNotFoundError:
        print("Error: 'serviceAccountKey.json' not found. Please output your Firebase Admin SDK private key here.")
        exit(1)
    except Exception as e:
        print(f"Error initializing Firebase: {e}")
        exit(1)


def detect_changes(old_data, new_data):
    """Compare old and new course data, return list of change descriptions."""
    changes = []

    old_sections = {s.get('class', ''): s for s in old_data.get('sections', []) if s.get('class')}
    new_sections = {s.get('class', ''): s for s in new_data.get('sections', []) if s.get('class')}

    # Sections removed (cancelled)
    for class_num, old_sec in old_sections.items():
        if class_num not in new_sections:
            changes.append(f"{old_sec.get('component', 'Unknown')} has been cancelled")

    # Sections added
    for class_num, new_sec in new_sections.items():
        if class_num not in old_sections:
            changes.append(f"{new_sec.get('component', 'Unknown')} has been added")

    # Sections changed
    for class_num in old_sections:
        if class_num in new_sections:
            old_sec = old_sections[class_num]
            new_sec = new_sections[class_num]

            if old_sec.get('time_date') != new_sec.get('time_date'):
                changes.append(
                    f"{new_sec.get('component', '')}: Time changed from "
                    f"{old_sec.get('time_date', 'TBA')} to {new_sec.get('time_date', 'TBA')}"
                )

            if old_sec.get('location') != new_sec.get('location'):
                changes.append(
                    f"{new_sec.get('component', '')}: Location changed from "
                    f"{old_sec.get('location', 'TBA')} to {new_sec.get('location', 'TBA')}"
                )

    return changes


def send_change_notification(doc_id, course, changes):
    """Send FCM data message to topic subscribers."""
    topic = f"course_{doc_id}"
    course_code = f"{course['subject']} {course['catalog']}"
    summary = "; ".join(changes[:3])  # Limit to 3 changes for notification body

    message = messaging.Message(
        data={
            "type": "course_change",
            "course_id": doc_id,
            "course_code": course_code,
            "change_summary": summary
        },
        topic=topic,
        android=messaging.AndroidConfig(priority="high")
    )

    try:
        response = messaging.send(message)
        print(f"    -> Notification sent for {course_code}: {response}")
    except Exception as e:
        print(f"    -> Failed to notify for {course_code}: {e}")


def populate_database(db, skip_notify=False):
    print("Fetching all subjects...")
    initial_sess = "1261"
    level = "under"

    subjects = scrape_schedule.get_all_subjects(level, initial_sess)
    print(f"Found {len(subjects)} subjects.")

    terms_to_scrape = ["1261", "1259", "1255"]

    batch = db.batch()
    batch_count = 0
    limit = 500
    total_changes = 0

    for subject in subjects:
        print(f"Processing Subject: {subject}")

        for sess in terms_to_scrape:
            courses = scrape_schedule.get_subject_courses(level, sess, subject)
            if not courses:
                continue

            for course in courses:
                doc_id = f"{sess}_{course['subject']}_{course['catalog']}"
                course['term'] = sess
                doc_ref = db.collection('courses').document(doc_id)

                # Check for changes before overwriting
                if not skip_notify:
                    try:
                        old_doc = doc_ref.get()
                        if old_doc.exists:
                            changes = detect_changes(old_doc.to_dict(), course)
                            if changes:
                                total_changes += len(changes)
                                print(f"  Changes detected in {doc_id}: {changes}")
                                send_change_notification(doc_id, course, changes)
                    except Exception as e:
                        print(f"  Error checking changes for {doc_id}: {e}")

                batch.set(doc_ref, course)
                batch_count += 1

                if batch_count >= limit:
                    print(f"Committing batch of {limit}...")
                    try:
                        batch.commit()
                    except Exception as e:
                        print(f"Error committing batch: {e}")
                    batch = db.batch()
                    batch_count = 0

    # Commit remaining
    if batch_count > 0:
        print(f"Committing final batch of {batch_count}...")
        try:
            batch.commit()
        except Exception as e:
            print(f"Error committing final batch: {e}")

    print(f"\nDatabase population complete! {total_changes} changes detected and notified.")


if __name__ == "__main__":
    db = initialize_firebase()
    skip_notify = "--skip-notify" in sys.argv
    if skip_notify:
        print("Skipping notifications (--skip-notify flag)")
    populate_database(db, skip_notify=skip_notify)
