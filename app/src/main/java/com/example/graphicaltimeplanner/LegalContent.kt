package com.example.graphicaltimeplanner

data class LegalSection(val heading: String? = null, val body: String)

object LegalContent {

    val TERMS_SECTIONS: List<LegalSection> = listOf(
        LegalSection(body = "Last updated: May 18, 2026"),
        LegalSection(
            body = "Welcome to Graphical Time Planner. By creating an account or using this app you agree to these Terms of Use. If you do not agree, please do not use the app."
        ),
        LegalSection(
            heading = "1. About the App",
            body = "Graphical Time Planner is an independent student project for University of Waterloo course scheduling. It is not affiliated with, endorsed by, or officially connected to the University of Waterloo."
        ),
        LegalSection(
            heading = "2. Eligibility",
            body = "You must be at least 13 years old to use the app. By using it you confirm that you meet this requirement."
        ),
        LegalSection(
            heading = "3. Your Account",
            body = "You are responsible for keeping your login credentials confidential and for all activity under your account. Provide accurate information when registering."
        ),
        LegalSection(
            heading = "4. AI-Generated Content — Important",
            body = "The app includes an AI assistant that generates schedule suggestions, academic advice, and advisor email drafts. AI-generated content may be incomplete, inaccurate, or outdated. It is not a substitute for official University of Waterloo course calendars, your academic advisor, or university policy. Always verify important academic decisions with official university sources before acting on them."
        ),
        LegalSection(
            heading = "5. File Uploads",
            body = "When you upload files (such as unofficial transcripts or course lists), the content is transmitted to our AI processing service for the duration of that request only. Only upload files that contain your own academic information. Do not upload files with sensitive personal data beyond what is needed for academic planning."
        ),
        LegalSection(
            heading = "6. Acceptable Use",
            body = "You agree not to: use the app for any unlawful purpose; attempt to access other users' data; abuse or overload our AI or backend services; misrepresent your identity or academic information."
        ),
        LegalSection(
            heading = "7. Disclaimer of Warranties",
            body = "The app is provided \"as is\" without warranties of any kind. We do not guarantee that the app will be error-free, uninterrupted, or that AI-generated advice will be accurate or suitable for your situation."
        ),
        LegalSection(
            heading = "8. Limitation of Liability",
            body = "To the maximum extent permitted by applicable law, the developers shall not be liable for any indirect, incidental, special, or consequential damages arising from your use of the app."
        ),
        LegalSection(
            heading = "9. Changes to These Terms",
            body = "We may update these Terms from time to time. Continued use of the app after changes are posted constitutes acceptance of the revised Terms."
        ),
        LegalSection(
            heading = "10. Contact",
            body = "Questions about these Terms? Contact us at d4yim@uwaterloo.ca."
        )
    )

    val PRIVACY_SECTIONS: List<LegalSection> = listOf(
        LegalSection(body = "Last updated: May 18, 2026"),
        LegalSection(
            body = "This Privacy Policy explains how Graphical Time Planner (\"we\", \"our\") collects, uses, and protects your information."
        ),
        LegalSection(
            heading = "1. Information We Collect",
            body = "• Account: email address and display name, stored via Firebase Authentication.\n" +
                   "• Academic profile: program, major, faculty, and year level you optionally provide in the Profile screen, stored in Firestore.\n" +
                   "• Timetables: courses and schedules you add, stored in Firestore.\n" +
                   "• Chat messages: conversations with the AI assistant stored in Firestore, capped at 30 sessions and 80 messages per session.\n" +
                   "• Uploaded files: when you upload a file in the chatbot, its text content is sent to our AI provider for processing. Files are not stored on our servers beyond the duration of that request.\n" +
                   "• Notification token: a Firebase Cloud Messaging token used solely to deliver course change notifications, stored only if you grant notification permission."
        ),
        LegalSection(
            heading = "2. How We Use Your Information",
            body = "• To provide the app's features: schedule building, AI-assisted planning, and course change notifications.\n" +
                   "• To personalise AI responses using your academic profile and timetable context.\n" +
                   "• To send push notifications about course schedule changes (only with your explicit permission)."
        ),
        LegalSection(
            heading = "3. Third-Party Services",
            body = "We use these third-party services, each with its own privacy policy:\n\n" +
                   "• Google Firebase (Authentication, Firestore, Cloud Messaging)\n" +
                   "• SiliconFlow API platform (AI language model — your messages and relevant context are sent to SiliconFlow when you use the assistant)\n" +
                   "• SerpAPI (web search used by the AI assistant)\n\n" +
                   "We encourage you to review their privacy policies. We do not sell your data to any third party."
        ),
        LegalSection(
            heading = "4. Data Retention",
            body = "Your account and timetable data are retained until you request deletion. Chat history is automatically capped as described above. Uploaded file content is not retained after the AI response is generated."
        ),
        LegalSection(
            heading = "5. Data Deletion",
            body = "To delete your account and all associated data, go to Profile → Delete Account. " +
                   "Your account, timetables, academic profile, and chat history will be permanently removed.\n\n" +
                   "If you are unable to access the app, email d4yim@uwaterloo.ca with subject \"Data Deletion Request\" " +
                   "and the email address associated with your account. "
        ),
        LegalSection(
            heading = "6. Security",
            body = "Your data is protected by Google Firebase's security infrastructure. No system is completely secure; please use a strong, unique password."
        ),
        LegalSection(
            heading = "7. Children's Privacy",
            body = "The app is not intended for users under 13 years of age. We do not knowingly collect personal information from children under 13."
        ),
        LegalSection(
            heading = "8. University Affiliation",
            body = "Graphical Time Planner is an independent student project. It is not an official product of the University of Waterloo."
        ),
        LegalSection(
            heading = "9. Changes to This Policy",
            body = "We may update this Privacy Policy. We will notify you of significant changes through the app."
        ),
        LegalSection(
            heading = "10. Contact",
            body = "Privacy questions or data deletion requests: d4yim@uwaterloo.ca."
        )
    )
}
