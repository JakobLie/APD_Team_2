# ----- Configuration -----
$jarPath = "target\se301-1.1-SNAPSHOT-jar-with-dependencies.jar" 
$inputFile = "datasets\large\in.txt"             # Input CSV file
$dictFile = "datasets\large\dictionary.txt"         # Dictionary file
$outputFile = "datasets\large\out.txt"              # Output file
$iterations = 30                              # Number of benchmark runs

# ----- Step 1: Build ----- 
Write-Host "Building project with Maven..." 
mvn clean package -q 
if ($LASTEXITCODE -ne 0) { 
    Write-Host "Maven build failed." 
    exit 1 
} 
Write-Host "Build complete.n"

# Only a single JVM invocation
Write-Host "Running benchmark (30 iterations) in a single JVM..."
java -Xms512m -Xmx512m -XX:+UseG1GC -jar $jarPath --bench $iterations $inputFile $dictFile $outputFile

# In APD_Team_2: powershell -ExecutionPolicy Bypass -File .\benchmark.ps1