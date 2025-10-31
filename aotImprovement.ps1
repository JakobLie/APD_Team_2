param (
    [int]$runsPerCycle = 10,        # how many runs inside each cycle (baseline vs AOT)
    [int]$cycles = 10,              # how many times to repeat the whole experiment
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
    & $java @args 2>$null
    $sw.Stop()
    return $sw.ElapsedMilliseconds
}

function Compute-Average80($times) {
    $sorted = $times | Sort-Object
    $cut = [math]::Ceiling($sorted.Count * 0.2)
    $trimmed = $sorted[$cut..($sorted.Count - 1)]
    return ($trimmed | Measure-Object -Average).Average
}

$java = "java"
$improvements = @()

for ($cycle = 1; $cycle -le $cycles; $cycle++) {
    Write-Host "CYCLE $cycle / $cycles"

    # --- Baseline runs ---
    $baselineTimes = @()
    for ($i = 1; $i -le $runsPerCycle; $i++) {
        $args = @("-jar", $jar, $inputFile, $dictFile, $outFile)
        $time = Measure-RunTime $java $args
        $baselineTimes += $time
    }

    # --- Generate AOT cache ---
    $aotArgs = @("-XX:AOTCacheOutput=$aotFile", "-jar", $jar, $inputFile, $dictFile, $outFile)
    & $java @aotArgs *> $null

    # --- AOT runs ---
    $aotTimes = @()
    for ($i = 1; $i -le $runsPerCycle; $i++) {
        $args = @("-XX:AOTCache=$aotFile", "-jar", $jar, $inputFile, $dictFile, $outFile)
        $time = Measure-RunTime $java $args
        $aotTimes += $time
    }

    # --- Compute averages ---
    $baselineAvg = Compute-Average80 $baselineTimes
    $aotAvg = Compute-Average80 $aotTimes
    $diff = $baselineAvg - $aotAvg
    $perc = ($diff / $baselineAvg) * 100
    $improvements += $perc
}

# --- Overall summary ---
$avgImprovement = ($improvements | Measure-Object -Average).Average

Write-Host "`n==========================================="
Write-Host ("FINAL SUMMARY (over {0} cycles of {1} runs each)" -f $cycles, $runsPerCycle)
Write-Host ("Average Improvement: {0:N2}%%" -f $avgImprovement)
Write-Host "==========================================="

# powershell -ExecutionPolicy Bypass -File .\aotImprovement.ps1