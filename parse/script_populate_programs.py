import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import re

"""

This script populates two Firestore collections:
- `programs`: All UW undergraduate programs
- `advisors`: Academic advisor emails per program/faculty, with first-year vs upper-year distinction

Data sourced from:
- Programs: https://uwaterloo.ca/future-students/programs
- Advisors: Individual faculty/department advising pages on uwaterloo.ca
"""

def slugify(name):
    """Convert a program name to a URL-friendly slug."""
    s = name.lower()
    s = re.sub(r'[^a-z0-9\s-]', '', s)
    s = re.sub(r'[\s]+', '-', s)
    s = re.sub(r'-+', '-', s)
    return s.strip('-')


# Each entry: (name, faculty, degreeType)
PROGRAMS = [
    ("Accounting and Financial Management", "Arts", "BAFM"),
    ("Actuarial Science", "Mathematics", "BMath"),
    ("Anthropology", "Arts", "BA"),
    ("Applied Mathematics", "Mathematics", "BMath"),
    ("Applied Mathematics with Scientific Computing and Scientific Machine Learning", "Mathematics", "BMath"),
    ("Architectural Engineering", "Engineering", "BASc"),
    ("Architecture", "Engineering", "BAS"),
    ("Bachelor of Arts", "Arts", "BA"),
    ("Bachelor of Science", "Science", "BSc"),
    ("Biochemistry", "Science", "BSc"),
    ("Biological and Medical Physics", "Science", "BSc"),
    ("Biology", "Science", "BSc"),
    ("Biomedical Engineering", "Engineering", "BASc"),
    ("Biomedical Sciences", "Science", "BSc"),
    ("Biostatistics", "Mathematics", "BMath"),
    ("Business Administration and Computer Science Double Degree", "Mathematics", "BBA/BCS"),
    ("Business Administration and Mathematics Double Degree", "Mathematics", "BBA/BMath"),
    ("Chemical Engineering", "Engineering", "BASc"),
    ("Chemistry", "Science", "BSc"),
    ("Civil Engineering", "Engineering", "BASc"),
    ("Classical Studies", "Arts", "BA"),
    ("Climate and Environmental Change", "Environment", "BES"),
    ("Combinatorics and Optimization", "Mathematics", "BMath"),
    ("Communication Studies", "Arts", "BA"),
    ("Computational Mathematics", "Mathematics", "BMath"),
    ("Computer Engineering", "Engineering", "BASc"),
    ("Computer Science", "Mathematics", "BCS"),
    ("Computing and Financial Management", "Mathematics", "BCFM"),
    ("Data Science", "Mathematics", "BMath"),
    ("Earth Sciences", "Science", "BSc"),
    ("Economics", "Arts", "BA"),
    ("Education", "Arts", "BA"),
    ("Electrical Engineering", "Engineering", "BASc"),
    ("English", "Arts", "BA"),
    ("Environment and Business", "Environment", "BES"),
    ("Environment Resources and Sustainability", "Environment", "BES"),
    ("Environmental Engineering", "Engineering", "BASc"),
    ("Environmental Sciences", "Science", "BSc"),
    ("Fine Arts", "Arts", "BA"),
    ("French", "Arts", "BA"),
    ("Gender and Social Justice", "Arts", "BA"),
    ("Geography and Aviation", "Environment", "BES"),
    ("Geography and Environmental Management", "Environment", "BES"),
    ("Geological Engineering", "Engineering", "BASc"),
    ("Geospatial Data Analysis", "Environment", "BES"),
    ("Global Business and Digital Arts", "Arts", "GBDA"),
    ("Health Sciences", "Health", "BScH"),
    ("History", "Arts", "BA"),
    ("Honours Arts", "Arts", "BA"),
    ("Honours Arts and Business", "Arts", "BA"),
    ("Honours Science", "Science", "BSc"),
    ("Human Rights and Law", "Arts", "BA"),
    ("Information Technology Management", "Mathematics", "BMath"),
    ("Kinesiology", "Health", "BScKin"),
    ("Legal Studies", "Arts", "BA"),
    ("Liberal Studies", "Arts", "BA"),
    ("Life Sciences", "Science", "BSc"),
    ("Management Engineering", "Engineering", "BASc"),
    ("Materials and Nanosciences", "Science", "BSc"),
    ("Mathematical Economics", "Mathematics", "BMath"),
    ("Mathematical Finance", "Mathematics", "BMath"),
    ("Mathematical Optimization", "Mathematics", "BMath"),
    ("Mathematical Physics", "Mathematics", "BMath"),
    ("Mathematical Studies", "Mathematics", "BMath"),
    ("Mathematics", "Mathematics", "BMath"),
    ("Mathematics Business Administration", "Mathematics", "BMath"),
    ("Mathematics Chartered Professional Accountancy", "Mathematics", "BMath"),
    ("Mathematics Financial Analysis and Risk Management", "Mathematics", "BMath"),
    ("Mathematics Teaching", "Mathematics", "BMath"),
    ("Mechanical Engineering", "Engineering", "BASc"),
    ("Mechatronics Engineering", "Engineering", "BASc"),
    ("Medical Sciences and Doctor of Medicine", "Science", "BSc/MD"),
    ("Medicinal Chemistry", "Science", "BSc"),
    ("Medieval Studies", "Arts", "BA"),
    ("Music", "Arts", "BA"),
    ("Nanotechnology Engineering", "Engineering", "BASc"),
    ("Optometry", "Science", "OD"),
    ("Peace and Conflict Studies", "Arts", "BA"),
    ("Pharmacy", "Science", "PharmD"),
    ("Philosophy", "Arts", "BA"),
    ("Physical Sciences", "Science", "BSc"),
    ("Physics", "Science", "BSc"),
    ("Physics and Astronomy", "Science", "BSc"),
    ("Planning", "Environment", "BES"),
    ("Political Science", "Arts", "BA"),
    ("Psychology BA", "Arts", "BA"),
    ("Psychology BSc", "Science", "BSc"),
    ("Public Health", "Health", "BPH"),
    ("Pure Mathematics", "Mathematics", "BMath"),
    ("Recreation and Leisure Studies", "Health", "BA"),
    ("Recreation Leadership and Health", "Health", "BA"),
    ("Religion Culture and Spirituality", "Arts", "BA"),
    ("Science and Aviation", "Science", "BSc"),
    ("Science and Business", "Science", "BSc"),
    ("Science and Financial Management", "Science", "BSc"),
    ("Sexualities Relationships and Families", "Arts", "BA"),
    ("Social Development Studies", "Arts", "BA"),
    ("Social Development Studies and BSW Double Degree", "Arts", "BA/BSW"),
    ("Social Work", "Arts", "BSW"),
    ("Sociology", "Arts", "BA"),
    ("Software Engineering", "Engineering", "BASc/BSE"),
    ("Sport and Recreation Management", "Health", "BA"),
    ("Statistics", "Mathematics", "BMath"),
    ("Sustainability and Financial Management", "Environment", "BES"),
    ("Systems Design Engineering", "Engineering", "BASc"),
    ("Theatre and Performance", "Arts", "BA"),
    ("Therapeutic Recreation", "Health", "BA"),
]

# Each entry: (programSlug or faculty key, email, advisorName, yearLevel)
# yearLevel: "first-year", "upper-year", or "all"
ADVISORS = [
    ("engineering-first-year", "firstyear.engineering@uwaterloo.ca", "Engineering First Year Office", "first-year"),

    # Engineering upper-year (per department)
    ("architectural-engineering", "archeng@uwaterloo.ca", "Mandeep Chahil", "upper-year"),
    ("chemical-engineering", "chemeng.undergrad.admin@uwaterloo.ca", "Denise Mueller", "upper-year"),
    ("civil-engineering", "cive.ug@uwaterloo.ca", "Shirley Springall", "upper-year"),
    ("environmental-engineering", "schneider@uwaterloo.ca", "Lisa Schneider", "upper-year"),
    ("geological-engineering", "schneider@uwaterloo.ca", "Lisa Schneider", "upper-year"),
    ("computer-engineering", "eceadvis@uwaterloo.ca", "Claire Fermin", "upper-year"),
    ("electrical-engineering", "eceadvis@uwaterloo.ca", "Claire Fermin", "upper-year"),
    ("management-engineering", "svossen@uwaterloo.ca", "Shelley Vossen", "upper-year"),
    ("mechanical-engineering", "mechadvisor@uwaterloo.ca", "Mechanical Engineering Advising", "upper-year"),
    ("mechatronics-engineering", "tronadvisor@uwaterloo.ca", "Mechatronics Engineering Advising", "upper-year"),
    ("software-engineering", "se-advisor@uwaterloo.ca", "Angie Hildebrand", "upper-year"),
    ("systems-design-engineering", "crystal.cooper@uwaterloo.ca", "Crystal Cooper", "upper-year"),
    ("biomedical-engineering", "crystal.cooper@uwaterloo.ca", "Crystal Cooper (SYDE office)", "upper-year"),
    ("nanotechnology-engineering", "chemeng.undergrad.admin@uwaterloo.ca", "Denise Mueller", "upper-year"),

    # ── Mathematics ──
    ("math-first-year", "mathadvisor@uwaterloo.ca", "Math Undergraduate Advising", "first-year"),
    ("computer-science", "csadvisor@uwaterloo.ca", "CS Advising Team", "all"),
    ("computing-and-financial-management", "bcfm@uwaterloo.ca", "Heather Shaw", "all"),
    ("data-science", "sasugradadv@uwaterloo.ca", "SAS Advising", "all"),
    ("actuarial-science", "sasugradadv@uwaterloo.ca", "SAS Advising", "all"),
    ("statistics", "sasugradadv@uwaterloo.ca", "SAS Advising", "all"),
    ("biostatistics", "sasugradadv@uwaterloo.ca", "SAS Advising", "all"),
    ("applied-mathematics", "applied.math.assocchair.ugrad@uwaterloo.ca", "Eduardo Martin-Martinez", "all"),
    ("applied-mathematics-with-scientific-computing-and-scientific-machine-learning", "applied.math.assocchair.ugrad@uwaterloo.ca", "Eduardo Martin-Martinez", "all"),
    ("mathematical-physics", "applied.math.assocchair.ugrad@uwaterloo.ca", "Eduardo Martin-Martinez", "all"),
    ("combinatorics-and-optimization", "coundergrad.officer@uwaterloo.ca", "Stephen Melczer", "all"),
    ("mathematical-optimization", "coundergrad.officer@uwaterloo.ca", "Stephen Melczer", "all"),
    ("computational-mathematics", "cmadvisor@uwaterloo.ca", "Computational Math Advising", "all"),
    ("pure-mathematics", "bmadill@uwaterloo.ca", "Blake Madill", "all"),
    ("mathematical-finance", "bmadill@uwaterloo.ca", "Blake Madill", "all"),
    ("mathematical-economics", "surya.banerjee@uwaterloo.ca", "Surya Banerjee", "all"),
    ("mathematics-business-administration", "michael.liu@uwaterloo.ca", "Michael Liu", "all"),
    ("mathematics-chartered-professional-accountancy", "peter.balka@uwaterloo.ca", "Peter Balka", "all"),
    ("mathematics-teaching", "mceden@uwaterloo.ca", "Mike Eden", "all"),
    ("mathematics-financial-analysis-and-risk-management", "farm.advisors@uwaterloo.ca", "FARM Advising", "all"),
    ("business-administration-and-mathematics-double-degree", "bbabmath-advisor@uwaterloo.ca", "BBA/BMath Advising", "all"),
    ("business-administration-and-computer-science-double-degree", "csadvisor@uwaterloo.ca", "CS Advising Team", "all"),
    ("information-technology-management", "michael.liu@uwaterloo.ca", "Michael Liu", "all"),

    # ── Arts ──
    ("arts-general", "artsadvisor@uwaterloo.ca", "Arts Undergraduate Office", "all"),
    ("accounting-and-financial-management", "safadvisor@uwaterloo.ca", "Roshni Verma", "all"),
    ("english", "englishadvisor@uwaterloo.ca", "Jenny Conroy", "all"),
    ("political-science", "psciadvising@uwaterloo.ca", "Lily Mackenzie", "all"),
    ("sustainability-and-financial-management", "jesse.macleod@uwaterloo.ca", "Jesse MacLeod", "all"),
    ("fine-arts", "berobert@uwaterloo.ca", "Brett Roberts", "all"),
    ("philosophy", "ggandres@uwaterloo.ca", "Greg Andres", "all"),
    ("psychology-ba", "cenver@uwaterloo.ca", "Ceylan Enver", "all"),

    # ── Science ──
    ("science-general", "science.advisor@uwaterloo.ca", "Science Undergraduate Office", "all"),

    # ── Environment ──
    ("climate-and-environmental-change", "gem-ug@uwaterloo.ca", "Crystal Vong", "all"),
    ("environment-and-business", "ug-seed@uwaterloo.ca", "Cheri Oestreich", "all"),
    ("environment-resources-and-sustainability", "sers-ug@uwaterloo.ca", "Patti Bester", "all"),
    ("geography-and-aviation", "gem-ug@uwaterloo.ca", "Sophie Dallaire", "all"),
    ("geography-and-environmental-management", "gem-ug@uwaterloo.ca", "Crystal Vong", "all"),
    ("geospatial-data-analysis", "gem-ug@uwaterloo.ca", "Crystal Vong", "all"),
    ("planning", "plan.undergradadvisor@uwaterloo.ca", "Jessica Huang", "all"),

    # ── Health ──
    ("kinesiology", "khs-kug@uwaterloo.ca", "Kinesiology Advising", "all"),
    ("health-sciences", "sphs-undergrad@uwaterloo.ca", "SPHS Advising", "all"),
    ("public-health", "sphs-undergrad@uwaterloo.ca", "SPHS Advising", "all"),
    ("recreation-and-leisure-studies", "rec-advising@uwaterloo.ca", "Sara Houston", "all"),
    ("recreation-leadership-and-health", "rec-advising@uwaterloo.ca", "Sara Houston", "all"),
    ("therapeutic-recreation", "rec-advising@uwaterloo.ca", "Sara Houston", "all"),
    ("sport-and-recreation-management", "rec-advising@uwaterloo.ca", "Sara Houston", "all"),
]

# Faculty-level fallback emails (used when no program-specific advisor exists)
FACULTY_FALLBACKS = {
    "Engineering": "firstyear.engineering@uwaterloo.ca",
    "Mathematics": "mathadvisor@uwaterloo.ca",
    "Arts": "artsadvisor@uwaterloo.ca",
    "Science": "science.advisor@uwaterloo.ca",
    "Environment": "gem-ug@uwaterloo.ca",
    "Health": "sphs-undergrad@uwaterloo.ca",
}


def initialize_firebase():
    try:
        cred = credentials.Certificate('parse/serviceAccountKey.json')
        firebase_admin.initialize_app(cred)
        return firestore.client()
    except FileNotFoundError:
        print("Error: 'serviceAccountKey.json' not found in parse/ directory.")
        exit(1)
    except Exception as e:
        print(f"Error initializing Firebase: {e}")
        exit(1)


def populate_programs(db):
    print("Populating programs collection...")
    batch = db.batch()
    batch_count = 0

    for name, faculty, degree_type in PROGRAMS:
        slug = slugify(name)
        doc_ref = db.collection('programs').document(slug)
        batch.set(doc_ref, {
            'name': name,
            'faculty': faculty,
            'degreeType': degree_type,
        })
        batch_count += 1

        if batch_count >= 500:
            print(f"  Committing batch of {batch_count}...")
            batch.commit()
            batch = db.batch()
            batch_count = 0

    if batch_count > 0:
        print(f"  Committing final batch of {batch_count}...")
        batch.commit()

    print(f"Done! {len(PROGRAMS)} programs written.")


def populate_advisors(db):
    print("Populating advisors collection...")
    batch = db.batch()
    batch_count = 0

    for program_slug, email, name, year_level in ADVISORS:
        # Document ID includes year level to allow multiple advisors per program
        doc_id = f"{program_slug}-{year_level}" if year_level != "all" else program_slug
        doc_ref = db.collection('advisors').document(doc_id)
        batch.set(doc_ref, {
            'programSlug': program_slug,
            'email': email,
            'name': name,
            'yearLevel': year_level,
        })
        batch_count += 1

        if batch_count >= 500:
            print(f"  Committing batch of {batch_count}...")
            batch.commit()
            batch = db.batch()
            batch_count = 0

    # Also write faculty fallback entries
    for faculty, email in FACULTY_FALLBACKS.items():
        slug = slugify(faculty)
        doc_id = f"{slug}-fallback"
        doc_ref = db.collection('advisors').document(doc_id)
        batch.set(doc_ref, {
            'programSlug': f"{slug}-fallback",
            'email': email,
            'name': f"{faculty} General Advising",
            'yearLevel': "all",
            'isFallback': True,
            'faculty': faculty,
        })
        batch_count += 1

    if batch_count > 0:
        print(f"  Committing final batch of {batch_count}...")
        batch.commit()

    print(f"Done! {len(ADVISORS) + len(FACULTY_FALLBACKS)} advisor entries written.")


if __name__ == "__main__":
    db = initialize_firebase()
    populate_programs(db)
    populate_advisors(db)
    print("\nAll collections populated successfully!")
