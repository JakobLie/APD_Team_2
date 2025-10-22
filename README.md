Course: SE301 – Advanced Programming and Design
Team: 2

Overview

This project is a refactored, concurrent, and high-performance password auditing tool.
It performs a dictionary attack on a list of SHA-256 password hashes to identify weak passwords efficiently.

The system replaces the legacy monolithic implementation with a modular, multi-threaded design that applies SOLID principles, an O(N + M) lookup optimization, and a live status reporter running on a separate thread.

Project Structure
.
├── src/                      # Java source code (Maven layout)
│   └── main/java/org/example/
│       ├── DictionaryAttack.java
│       ├── engine/           # Core concurrent cracking engine
│       ├── io/               # User & dictionary loaders
│       └── reporter/         # Live status reporter
├── datasets/
│   ├── small/                # Small test data set
│   └── large/                # Large benchmark data set
├── target/                   # Maven build output
├── pom.xml                   # Maven project configuration
└── README.md


Building the Project

From the project root (where pom.xml is):

mvn clean package

After a successful build, the runnable JAR (with dependencies) will be in target/, for example:

target/se301-1.1-SNAPSHOT-jar-with-dependencies.jar

Running the Application

Command format:

java -jar <path_to_jar_file> <path_to_in.txt> <path_to_dictionary.txt> <path_to_out.txt>

Example (Unix / macOS / Linux)
cd /path/to/project
java -jar target/se301-1.1-SNAPSHOT-jar-with-dependencies.jar \
    datasets/small/in.txt \
    datasets/small/dictionary.txt \
    datasets/small/out2.txt

Example (Windows PowerShell)
cd C:\path\to\project
java -jar target\se301-1.1-SNAPSHOT-jar-with-dependencies.jar `
    .\datasets\small\in.txt `
    .\datasets\small\dictionary.txt `
    .\datasets\small\out2.txt

Input / Output formats
in.txt

Each line:

username,hashed_password

where hashed_password is the lowercase hex SHA-256.

dictionary.txt

Plain text, one candidate password per line.

out.txt (generated)

CSV with header:

user_name,hashed_password,plain_password

Order of rows in out.txt is not important.

What to submit (per spec)

src/ – complete source in Maven layout.

run.jar – runnable JAR with dependencies (example target/se301-1.1-SNAPSHOT-jar-with-dependencies.jar).

Authors

Team 2 – Advanced Programming and Design (SE301)
Chun Yik
Lie Wie Yong Jakob
Shin Leng