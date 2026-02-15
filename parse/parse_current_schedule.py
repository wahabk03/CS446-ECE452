import re
import json

def parse_current_schedule(text: str):
    '''
    Parse raw text from UWaterloo Quest "My Class Schedule" page
    (e.g. Winter 2026 enrolled classes)

    Returns: list of course dictionaries
    Each course may have multiple sections (e.g. LEC + TUT)
    '''
    lines = text.strip().split('\n')

    courses = []
    current_term = None
    current_course = None
    current_section = None
    in_schedule = False

    for line in lines:
        # 1. Detect term (appears once near top)
        term_match = re.search(r'^(Fall|Winter|Spring)\s+\d{4}', line, re.I)
        if term_match:
            current_term = term_match.group(0)
            continue

        # 2. Detect start of enrolled schedule content
        if 'Show Enrolled Classes' in line or 'My Class Schedule' in line:
            in_schedule = True
            continue

        # 3. End marker
        if in_schedule and 'Printer Friendly Page' in line:
            if current_course:
                courses.append(current_course)
            break

        if not in_schedule:
            continue

        # 4. New course block starts with:  CODE - Title
        course_header_match = re.match(r'^([A-Z]{2,6}\s+\d+[A-Za-z]?)\s*-\s*(.+)$', line)
        if course_header_match:
            # Save previous course if exists
            if current_course:
                courses.append(current_course)

            code = course_header_match.group(1).strip()
            title = course_header_match.group(2).strip().rstrip()

            current_course = {
                'Term': current_term,
                'Code': code,
                'Description': title,
                'Status': None,
                'Units': None,
                'Grading': None,
                'Sections': []
            }
            current_section = None
            continue

        if not current_course:
            continue

        # 5. Status / Units / Grading lines (usually right after title)
        if line in ('Enrolled', 'Waitlisted', 'Dropped'):
            current_course['Status'] = line
            continue
        if re.match(r'^\d\.\d{2}$', line):
            current_course['Units'] = line
            continue
        if 'Numeric Grading Basis' in line:
            current_course['Grading'] = 'Numeric'
            continue

        # 6. Section table rows (each field on its own line)
        # We look for known patterns in sequence
        if re.match(r'^\d{4}$', line):  # Class Nbr (4 digits)
            if current_section:  # save previous section if complete
                current_course['Sections'].append(current_section)

            current_section = {
                'ClassNbr': line,
                'Section': None,
                'Component': None,
                'DaysTimes': None,
                'Room': None,
                'Instructor': None,
                'Dates': None
            }
            continue

        if current_section:
            if current_section['Section'] is None:
                # Section number (usually 3 digits)
                if re.match(r'^\d{3}$', line):
                    current_section['Section'] = line
                    continue

            if current_section['Component'] is None:
                # LEC / TUT / SEM / etc.
                if line in ('LEC', 'TUT', 'SEM', 'LAB', 'PRJ'):
                    current_section['Component'] = line
                    continue

            if current_section['DaysTimes'] is None:
                # e.g. TTh 14:30 - 15:50  or  MWF 13:30 - 14:20
                if re.search(r'[MTWRF]+\s+\d{2}:\d{2}\s*-\s*\d{2}:\d{2}', line):
                    current_section['DaysTimes'] = line
                    continue

            if current_section['Room'] is None:
                # e.g. HH 1101, MC 2065, CGR 1111
                if re.match(r'^[A-Z]{2,4}\s*\d{4}$|^[A-Z]{3}\s*\d{4}$', line) or ' ' in line:
                    current_section['Room'] = line
                    continue

            if current_section['Instructor'] is None:
                # Instructor name (usually two words or more)
                if ' ' in line and not re.search(r'\d', line):
                    current_section['Instructor'] = line
                    continue

            if current_section['Dates'] is None:
                # e.g. 05/01/2026 - 06/04/2026
                if re.match(r'^\d{2}/\d{2}/\d{4}\s*-\s*\d{2}/\d{2}/\d{4}$', line):
                    current_section['Dates'] = line
                    # section complete
                    current_course['Sections'].append(current_section)
                    current_section = None
                    continue

    # Don't forget the last course & last section
    if current_course:
        if current_section:
            current_course['Sections'].append(current_section)
        courses.append(current_course)

    return courses

with open("parse/test_file/current_schedule.txt", "r") as f:
    current_schedule = f.read()

# print(current_schedule)

parsed_current_schedule = parse_current_schedule(current_schedule)

json_string = json.dumps(parsed_current_schedule,indent=4)

print(json_string)