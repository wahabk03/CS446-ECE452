# Tools for the AI Agent

def read_uploaded_file(file_path: str) -> str:
    """
    Read the contents of an uploaded file (like a transcript or PDF).
    """
    print(f"Reading file from {file_path}")
    # TODO: Implement file parsing (text, PDF, etc.)
    with open(file_path, 'r', encoding='utf-8') as f:
        return f.read()

def browse_online(query: str) -> str:
    """
    Simulates browsing the web for missing prerequisite or course info.
    """
    print(f"Browsing online for: {query}")
    # TODO: Implement a web search API (e.g. SerpAPI, Google Search API)
    return "Mock web search result for: " + query

def query_database_readonly(collection: str, document_id: str = None) -> dict:
    """
    Accesses the timetable/courses database with read-only permissions.
    """
    print(f"Querying database - Collection: {collection}, Document: {document_id}")
    # TODO: Implement read-only connection to Firebase / Firestore or similar database
    if document_id:
        return {"mock_data": f"Details for {document_id} in {collection}"}
    return {"mock_data": f"All data snippet for {collection}"}
