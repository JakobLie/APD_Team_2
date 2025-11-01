# ----- Configuration -----
$jarPath    = "target\se301-1.1-SNAPSHOT-jar-with-dependencies.jar"
$inputFile  = "datasets\large\in.txt"        # Input CSV file
$dictFile   = "datasets\large\dictionary.txt" # Dictionary file
$outputFile = "datasets\large\out.txt"        # Output file
$iterations = 30                              # Number of runs

# ----- Step 1: Build -----
Write-Host "Building project with Maven..."
mvn clean package -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed."
    exit 1
}
Write-Host "Build complete."

# ----- Step 2: Benchmark -----
Write-Host "Running benchmark ($iterations iterations)..."

# Store all times
$times = @()

for ($i = 1; $i -le $iterations; $i++) {
    # Record start time
    $startTime = Get-Date

    # Run the JAR (one full JVM invocation per iteration)
    java -Xms512m -Xmx512m -XX:+UseG1GC -jar $jarPath $inputFile $dictFile $outputFile | Out-Null

    # Record end time
    $endTime = Get-Date

    # Compute elapsed milliseconds
    $elapsedMs = ($endTime - $startTime).TotalMilliseconds
    $times += [math]::Round($elapsedMs, 2)
}

# ----- Step 3: Results -----
$median = ($times | Sort-Object)[[int]($times.Count / 2)]
$avg = ($times | Measure-Object -Average).Average

Write-Host "================ Benchmark Summary ================"
Write-Host ("Iterations: {0}" -f $iterations)
Write-Host ("Median time : {0:N2} ms" -f $median)
Write-Host ("Average time: {0:N2} ms" -f $avg)
Write-Host "==================================================="

# powershell -ExecutionPolicy Bypass -File .\benchmark.ps1