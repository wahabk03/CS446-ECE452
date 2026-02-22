import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import scrape_schedule
import time

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
"""

def initialize_firebase():
    # Fetch the service account key JSON file contents
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

def populate_database(db):
    print("Fetching all subjects...")
    # Using the same semester code as scrape_schedule.py example: "1261" (Winter 2026)
    # You might want to make this dynamic or passed as argument
    sess = "1261" 
    level = "under"
    
    subjects = scrape_schedule.get_all_subjects(level, sess)
    print(f"Found {len(subjects)} subjects.")

    # Create a batch handler for efficient rights
    batch = db.batch()
    batch_count = 0
    limit = 500  # Firestore batch limit is 500

    for subject in subjects:
        print(f"Fetching courses for subject: {subject}")
        courses = scrape_schedule.get_subject_courses(level, sess, subject)
        
        for course in courses:
            # Document ID structure: "1261_CS_136"
            doc_id = f"{sess}_{course['subject']}_{course['catalog']}"
            
            # Prepare data
            doc_ref = db.collection('courses').document(doc_id)
            
            # Add metadata about term
            course['term'] = sess
            
            batch.set(doc_ref, course)
            batch_count += 1

            if batch_count >= limit:
                print("Committing batch...")
                batch.commit()
                batch = db.batch() # Start new batch
                batch_count = 0
        
        # Be nice to the server
        time.sleep(0.5)

    # Commit any remaining operations
    if batch_count > 0:
        print("Committing final batch...")
        batch.commit()

    print("Database population complete!")

if __name__ == "__main__":
    db = initialize_firebase()
    populate_database(db)
