<#
Author: Dwyane
Description: Invoke the Rest Api Call from powershell script inside Jenkins to reach Jira, which will add the user to the proper Project & Group giving them access, and then sending a message back to SNOW that the access has been granted. 
Date: 6/10/2020
#>

#Initialize variable

#Mapped to Jenkins UI from field SNOW data, String Parameter Jenkins 
$userField = $env:user
$passwordField = $env:password


#Initialize variables for the payload to send out to SNOW ticket
$devUriJira = $env:uri

#Use case with a SNOW specific ticket
$body = @{
 "user" = $env:user
 "message" = "Jira Access Requested is processed for $userField, please validate"
 "status" = "Ok"
}

$secpasswd = ConvertTo-SecureString $passwordField -AsPlainText -Force
$mycreds = New-Object System.Management.Automation.PSCredential($userField, $secpasswd)

# Setting the Allowed Protocols
$AllProtocols = [System.Net.SecurityProtocolType]'Ssl3,Tls,Tls11,Tls12'
[System.Net.ServicePointManager]::SecurityProtocol = $AllProtocols
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $True }

Invoke-RestMethod -Uri $devUriJira -Method GET -Credential $mycreds -ContentType "application/json" #Make the rest call to update the ticket
