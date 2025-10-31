param ( 
    [int]$runs = 10, 
    [string]$datasetDir = "datasets\large", 
    [string]$jar = "target\se301-1.1-SNAPSHOT-jar-with-dependencies.jar" 
) 
    
# Dataset and output paths 
$inputFile = Join-Path $datasetDir "in.txt" 
$dictFile = Join-Path $datasetDir "dictionary.txt" 
$outFile = Join-Path $datasetDir "AOTout.txt" 
$aotFile = "app.aot" 

# Helper function to measure elapsed time 
function Measure-RunTime($java, $args) { 
    $sw = [System.Diagnostics.Stopwatch]::StartNew() 
    & $java @args 2>$null # redirect stderr to null 
    $sw.Stop() 
    return $sw.ElapsedMilliseconds 
} 

$java = "java" 

# ----------------- BASELINE RUNS ----------------- 
Write-Host "===== BASELINE RUNS (No AOT) =====" 
$baselineTimes = @() 
for ($i = 1; $i -le $runs; $i++) { 
    $args = @("-jar", $jar, $inputFile, $dictFile, $outFile) 
    $time = Measure-RunTime $java $args 
    $baselineTimes += $time 
    Write-Host "Run ${i}: $time ms" } 
    
# ----------------- GENERATE AOT CACHE ----------------- 
Write-Host "n===== GENERATING AOT CACHE =====" 
$aotArgs = @("-XX:AOTCacheOutput=$aotFile", "-jar", $jar, $inputFile, $dictFile, $outFile) 
Write-Host "Generating AOT cache..." 
& $java @aotArgs *> $null 

# ----------------- AOT BENCHMARK RUNS ----------------- 
Write-Host "n===== AOT RUNS =====" 
$aotTimes = @() 
for ($i = 1; $i -le $runs; $i++) { 
    $args = @("-XX:AOTCache=$aotFile", "-jar", $jar, $inputFile, $dictFile, $outFile) 
    $time = Measure-RunTime $java $args 
    $aotTimes += $time 
    Write-Host "Run ${i}: $time ms" } 
    
# ----------------- COMPUTE 80% AVERAGE ----------------- 
function Compute-Average80($times) { 
    $sorted = $times | Sort-Object 
    $cut = [math]::Ceiling($sorted.Count * 0.2) 
    $trimmed = $sorted[$cut..($sorted.Count - 1)] 
    return ($trimmed | Measure-Object -Average).Average 
} 

$baselineAvg = Compute-Average80 $baselineTimes 
$aotAvg = Compute-Average80 $aotTimes 

# ----------------- RESULTS ----------------- 
Write-Host "n===== RESULTS =====" 
Write-Host ("Baseline avg (80%): {0:N0} ms" -f $baselineAvg) 
Write-Host ("AOT avg (80%): {0:N0} ms" -f $aotAvg) 
$diff = $baselineAvg - $aotAvg 
$perc = ($diff / $baselineAvg) * 100 
Write-Host ("Improvement: {0:N0} ms ({1:N2}%)" -f $diff, $perc)

# powershell -ExecutionPolicy Bypass -File .\aotrunonce.ps1