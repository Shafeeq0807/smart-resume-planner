from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas
import docx

LINES = [
 ("Priya Sharma", "h1"),
 ("Senior Java Developer", "p"),
 ("priya.sharma@example.com | +91 98765 43210 | Bengaluru, India | linkedin.com/in/priyasharma | github.com/priyasharma", "p"),
 ("SUMMARY", "h2"),
 ("Backend engineer with 6 years of experience building REST APIs and microservices with Java and Spring Boot.", "p"),
 ("EXPERIENCE", "h2"),
 ("Senior Software Engineer | Acme Technologies Pvt Ltd | Bengaluru", "p"),
 ("Jan 2021 - Present", "p"),
 ("• Responsible for maintaining the payment service written in Java and Spring Boot", "p"),
 ("• Reduced API latency by 38% by adding Redis caching and tuning MySQL queries", "p"),
 ("• Helped with migration of the monolith to microservices on Docker and Kubernetes", "p"),
 ("• Led a team of 4 engineers and mentored two juniors", "p"),
 ("Software Engineer | Globex Solutions | Pune", "p"),
 ("Jun 2018 - Dec 2020", "p"),
 ("• Worked on REST APIs for the customer portal using Spring and Hibernate", "p"),
 ("• Built CI/CD pipelines with Jenkins and Maven, cutting release time from 2 hours to 25 minutes", "p"),
 ("• Wrote unit tests with JUnit and Mockito", "p"),
 ("EDUCATION", "h2"),
 ("B.Tech in Computer Science | National Institute of Technology | 2014 - 2018", "p"),
 ("SKILLS", "h2"),
 ("Languages: Java, SQL, Python, JavaScript", "p"),
 ("Frameworks: Spring Boot, Spring Security, Hibernate, React", "p"),
 ("Tools: Docker, Kubernetes, Jenkins, Git, Maven, MySQL, Redis, Kafka", "p"),
 ("PROJECTS", "h2"),
 ("Resume Analyzer", "p"),
 ("• Built a Spring Boot service that parses resumes with Apache Tika and scores them", "p"),
 ("Technologies: Java, Spring Boot, MySQL, Docker", "p"),
 ("CERTIFICATIONS", "h2"),
 ("• AWS Certified Developer - Associate", "p"),
]

def pdf(path):
    c = canvas.Canvas(path, pagesize=A4)
    y = 800
    for text, kind in LINES:
        size = 18 if kind == "h1" else 12 if kind == "h2" else 10
        font = "Helvetica-Bold" if kind in ("h1", "h2") else "Helvetica"
        c.setFont(font, size)
        # reportlab base fonts: keep ASCII bullet substitute
        c.drawString(50, y, text.replace("•", "•"))
        y -= 22 if kind == "h2" else 16
    c.save()

def word(path):
    d = docx.Document()
    for text, kind in LINES:
        if kind == "h1":
            d.add_heading(text, 0)
        elif kind == "h2":
            d.add_heading(text, 2)
        else:
            d.add_paragraph(text)
    d.save(path)

pdf("resume.pdf")
word("resume.docx")
# second variant (different file) for versioning
LINES[8] = ("• Owned the payment service (Java, Spring Boot) handling 1.2M requests per day", "p")
pdf("resume_v2.pdf")
open("not_a_resume.pdf", "wb").write(b"MZ this is not a pdf at all" * 20)
print("ok")
