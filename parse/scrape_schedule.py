import requests
import json
from bs4 import BeautifulSoup

# returns a list of all subjects
# e.g. ['ACC', 'ACINITY', ...]
def get_all_subjects(level: str, sess: str):
  # url to get all subjects
  url = "https://classes.uwaterloo.ca/under.html"

  params = {
      "level": level,
      "sess": sess
      }

  subjects = []

  # make a POST request
  response = requests.post(url, data=params)

  if response.status_code == 200:
    soup = BeautifulSoup(response.text, "lxml")

    # print(soup)
    subject_select = soup.find("select", id="ssubject")
    # print(subject_select)
    options = subject_select.find_all("option")
    # print(options)
    subjects = [opt["value"] for opt in options]
    # print(subjects)
    

  else:
      print("Failed to fetch data:", response.status_code)
    
  return subjects

def get_subject_courses(level: str, sess: str, subject: str):

  def parse_courses(soup):
    main_table = soup.find("table", border="2")

    if not main_table:
        return []   # No results

    courses = []
    rows = main_table.find_all("tr", recursive=False)

    i = 0
    while i < len(rows):
        row = rows[i]
        cols = row.find_all("td")

        # Course row has exactly 4 columns
        if len(cols) == 4:
            subject = cols[0].get_text(strip=True)
            catalog = cols[1].get_text(strip=True)
            units = cols[2].get_text(strip=True)
            title = cols[3].get_text(strip=True)

            course = {
                "subject": subject,
                "catalog": catalog,
                "units": units,
                "title": title,
                "sections": []
            }

            # Next few rows contain notes + section table
            # Look ahead for nested table
            j = i + 1
            while j < len(rows):
                nested_table = rows[j].find("table")
                if nested_table:
                    section_rows = nested_table.find_all("tr")[1:]  # skip header

                    for srow in section_rows:
                        scols = srow.find_all("td")
                        if len(scols) >= 10:  # actual section rows
                            section = {
                                "class": scols[0].get_text(strip=True),
                                "component": scols[1].get_text(strip=True),
                                "campus": scols[2].get_text(strip=True),
                                "enrl_cap": scols[6].get_text(strip=True),
                                "enrl_tot": scols[7].get_text(strip=True),
                                "wait_cap": scols[8].get_text(strip=True),
                                "wait_tot": scols[9].get_text(strip=True),
                            }
                            course["sections"].append(section)
                    break
                j += 1

            courses.append(course)

        i += 1

    return courses


  url = "https://classes.uwaterloo.ca/cgi-bin/cgiwrap/infocour/salook.pl"

  params = {
      "level": level,
      "sess": sess,       
      "subject": subject,      
      "cournum":	""
  }

  response = requests.post(url, data=params)

  courses = []

  if response.status_code == 200:
    soup = BeautifulSoup(response.text, "lxml")
    courses = parse_courses(soup)

  else:
    print("Failed to fetch data:", response.status_code)
  
  return courses




# def get_course_data():
#   # Example: specify term and subject parameters
#   # You must inspect the actual form to determine parameter names
#   params = {
#       "level": "under",
#       "sess": "1261",        # Example: Winter 2026
#       "subject": "CS",       # Computer Science
#       "cournum":	""
#       # Add other parameters if required
      
#   }

#   url = "https://classes.uwaterloo.ca/cgi-bin/cgiwrap/infocour/salook.pl"

#   # Make a GET request with query parameters
#   response = requests.post(url, data=params)
#   # print(response)
#   # Check if it succeeded
#   if response.status_code == 200:
#       soup = BeautifulSoup(response.text, "html.parser")
#       # print(soup)
      
#       # Example: find course blocks (adjust based on actual HTML tags/classes)
#       # Here we look for <table> or <div> with course info
#       # courses = soup.find_all("tr")  # for example; real selector may differ

#       # for row in courses:
#       #     cells = row.find_all("td")
#       #     if len(cells) >= 3:
#       #         course_code = cells[0].text.strip()
#       #         course_title = cells[1].text.strip()
#       #         enroll_info = cells[2].text.strip()
#       #         print(course_code, course_title, enroll_info)
#   else:
#       print("Failed to fetch data:", response.status_code)

if __name__ == "__main__":
    level = "under"
    sess = "1261"
    # subjects = get_all_subjects("under", "1261")
    # print(subjects)
    courses = get_subject_courses(level, sess, "CS")

    # with open('parse/example_output/scraped_schedule.txt','w+') as file:
    #     for course in courses:
    #         file.write(f"Subject: {course['subject']} | "
    #                 f"Catalog: {course['catalog']} | "
    #                 f"Units: {course['units']} | "
    #                 f"Title: {course['title']}\n")
    #         for section in course['sections']:
    #             file.write(f"Class: {section['class']} | "
    #                 f"Component: {section['component']} | "
    #                 f"Campus: {section['campus']} | "
    #                 f"Enrl_cap: {section['enrl_cap']} | "
    #                 f"Eenrl_tot: {section['enrl_tot']} | "
    #                 f"Wait_cap: {section['wait_cap']} | "
    #                 f"Wait_tot: {section['wait_tot']}\n")
    #         file.write('\n')

    json_string = json.dumps(courses,indent=4)