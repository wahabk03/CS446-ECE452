from tika import parser # pip install tika
import re

def parse_transcript(text:str):
    '''
    Parse raw text from UWaterloo unofficial transcript

    Returns a dictionary with student info and list of courses
    '''

    lines = text.strip().split('\n')
    result = {
        'Name': None,
        'SID': None,
        'Program': None,
        'CoursesTaken': []
    }
    in_courses = False
    current_term = None

    for line in lines:
        line = line.strip()
        if not line:
            continue  # Skip empty lines

        # Extract student info (key-value pairs)
        if line.startswith('Name:'):
            result['Name'] = re.sub(r'\s+', ' ', line.split(':', 1)[1].strip())
        elif line.startswith('Student ID:'):
            result['SID'] = line.split(':', 1)[1].strip()
        elif line.startswith('Program:'):
            result['Program'] = re.sub(r'\s+', ' ', line.split(':', 1)[1].strip())

        # Detect term (e.g., "Winter 2026")
        elif re.match(r'^(Fall|Winter|Spring)\s+\d{4}$', line):
            current_term = line
            continue  # Courses follow soon after
        # Start of course table
        elif line == 'Course Description Attempted Earned Grade':
            in_courses = True
            continue

        # End of transcript
        elif in_courses and line.startswith('End of'):
            in_courses = False
            continue

        # Parse course lines
        elif in_courses:
            # Normalize spaces
            line = re.sub(r'\s{2,}', ' ', line)
            
            pattern = r'^([A-Z]{2,6})\s+(\d{1,3}[A-Za-z]?)\s+(.+?)\s+(\d\.\d{2})\s+(\d\.\d{2})\s+([A-Z0-9]{1,3})$'
            match = re.match(pattern,line)
            if match:
                subject, number, title, attempted, earned, grade = match.groups()
                
                course = {
                    'Term': current_term,
                    'Code': f"{subject} {number}",  # e.g., "CS 446"
                    'Description': title.strip(),
                    'Attempted': attempted,
                    'Earned': earned,
                    'Grade': grade
                }
                result['CoursesTaken'].append(course)

    return result

# step 1 : parse text from transcript

raw = parser.from_file("SSR_TSRPT1.pdf")

'''
with open('parse_transcript.txt','w+') as file:
    file.write(raw["content"])
'''

# step 2 : extract useful info

parsed_data = parse_transcript(raw["content"])

with open("parsed_transcript.txt","w+",encoding="utf-8") as file:
    file.write(f"Student: {parsed_data['Name'] or '-'}\n")
    file.write(f"SID:      {parsed_data['SID'] or '-'}\n")
    file.write(f"Program: {parsed_data['Program'] or '-'}\n")
    file.write('\n')
    for course in parsed_data['CoursesTaken']:
        # 'Term': 'Fall 2017', 'Code': 'CS 137', 'Description': 'Programming Principles', 'Attempted': '0.50', 'Earned': '0.50', 'Grade': '86'
        file.write(
            f"Term: {course['Term']} | "
            f"Code: {course['Code']} | "
            f"Description: {course['Description']} | "
            f"Attempted: {course['Attempted']} | "
            f"Earned: {course['Earned']} | "
            f"Grade: {course['Grade']}\n"
        )
