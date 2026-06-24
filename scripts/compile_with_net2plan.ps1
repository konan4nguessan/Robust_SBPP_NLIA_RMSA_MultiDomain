$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$LibDir = "C:\Memoire-Projet-EON\Net2Plan\Net2Plan-0.7.0.1\lib"
$OutDir = Join-Path $ProjectRoot "bin_check"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$AllJars = Get-ChildItem -LiteralPath $LibDir -Filter *.jar
$PreferredJarNames = @(
    "parallelcolt-parallelcolt-0.11.4.jar",
    "parallelcolt-0.10.1.jar",
    "net2plan-core.jar"
)
$PreferredJars = foreach ($JarName in $PreferredJarNames) {
    $AllJars | Where-Object { $_.Name -eq $JarName }
}
$OtherJars = $AllJars | Where-Object { $PreferredJarNames -notcontains $_.Name }
$Classpath = (($PreferredJars + $OtherJars) | ForEach-Object { $_.FullName }) -join ";"
$Sources = Get-ChildItem -Recurse -Path (Join-Path $ProjectRoot "src") -Filter *.java | ForEach-Object { $_.FullName }

javac -source 1.8 -target 1.8 -cp $Classpath -d $OutDir $Sources

Write-Host "Compiled with Net2Plan jars into $OutDir"


