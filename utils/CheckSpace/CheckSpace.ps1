$minsize = 10000000000000000
$num = 5
$notify = "false"
$drives = (Get-Volume).DriveLetter
$sizes = (Get-Volume).SizeRemaining


foreach ($i in $sizes) {
    if ($sizes[$i] -lt $minsize) {
        $notify = "true"
    }
}

if ($notify -eq "true") {
    foreach ($letter in $drives) {
        Write-Output ("Largest " + $num + " files in " + $letter + ":\ drive:")
        Get-ChildItem ($letter + ":\") -r -ErrorAction silentlycontinue | Sort-Object -descending -property length | Select-Object -first $num name, Length
    }
}