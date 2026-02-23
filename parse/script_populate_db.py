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
    # Terms: 1261 (Winter 2026), 1259 (Fall 2025), 1255 (Spring 2025)
    # Using 1261 for initial subject list
    initial_sess = "1261"
    level = "under"
    
    subjects = scrape_schedule.get_all_subjects(level, initial_sess)
    print(f"Found {len(subjects)} subjects.")

    # List of terms to scrape
    terms_to_scrape = ["1261", "1259", "1255"]

    # Create a batch handler for efficient rights
    batch = db.batch()
    batch_count = 0
    limit = 500  # Firestore batch limit is 500

    # Terms to scrape: Winter 2026 (1261), Fall 2025 (1259), Spring 2025 (1255)
    terms_to_scrape = ["1261", "1259", "1255"]
    
    # Iterate through all subjects
    for subject in subjects:
        print(f"Processing Subject: {subject}")
        
        # Scrape all specified terms
        for sess in terms_to_scrape:
            
            courses = scrape_schedule.get_subject_courses(level, sess, subject)
            
            if not courses:
                continue

            for course in courses:
                # Document ID structure: "1261_CS_136" (Term_Subject_Catalog)
                doc_id = f"{sess}_{course['subject']}_{course['catalog']}"
                
                # Add metadata about term
                course['term'] = sess
                doc_ref = db.collection('courses').document(doc_id)
                
                batch.set(doc_ref, course)
                batch_count += 1

                if batch_count >= limit:
                    print(f"Committing batch of {limit}...")
                    try:
                        batch.commit()
                    except Exception as e:
                        print(f"Error committing batch: {e}")
                    
                    batch = db.batch() # Start new batch
                    batch_count = 0
            
            # Tiny sleep between terms
            # time.sleep(0.05)

    # Commit any remaining operations
    if batch_count > 0:
        print(f"Committing final batch of {batch_count}...")
        try:
            batch.commit()
        except Exception as e:
            print(f"Error committing final batch: {e}")

    print("Database population complete!")

if __name__ == "__main__":
    db = initialize_firebase()
    populate_database(db)
