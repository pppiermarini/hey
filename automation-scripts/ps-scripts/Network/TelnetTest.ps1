<#
Authors: SCM
Description: Run a telnet for multiple hosts
Date: 3/2/2020
#>

#Add a comma delimtted list of Computer Names
$hostName = $args[0]

#The Porttelnet  
$Port = $args[1]

#Current Host
$curHost = $env:COMPUTERNAME


$Socket = New-Object Net.Sockets.TcpClient

# Suppress error messages
$ErrorActionPreference = 'SilentlyContinue'
# Try to connect
$Socket.Connect($hostName, $Port)

# Make error messages visible again
$ErrorActionPreference = 'Continue'

# Determine if we are connected.
if ($Socket.Connected) {
    "From ${curHost} to ${hostName}: Port $Port is open"
    $Socket.Close()
}
else {
    "From ${curHost} to ${hostName}: Port $Port is closed or filtered"  
}
#Setting Obj to null when completed 
$Socket = $null