<#
Authors: SCM & SCM
Description: Automate SNOW ticket requests for Dev Tools Access related updates
Date: 4/10/2020
#>


#Initialize variable

#Mapped to Jenkins UI from field SNOW data, String Parameter Jenkins 
#TODO: Update based on JIRA Dev Tool Req. from SNOW ticket
$ProjectKeyField = $env:project_key
$AccessTypeField = $env:access_type

#TODO: @Dwayne - DEVOPS-1156 & DEVOPS-1157
$userIDField = $env:user_id
$domainNameField = $env:domain_name
$accessServersField = $env:access_servers
$reqIDField = $env:req_id



#Initialize variables for the payload to send out to SNOW ticket
$devUri = $env:uri

#@TODO: Might be ok to remove
$header = @{
Authorization = $env:Auth

}

#@TODO: Figure out a use case with a SCM specific ticket
$body = @{
 "reqId" = $env:req_id
 "message" = "Jira Access Requested is processed for $userIDField, please validate"
 "status" = "Ok"
}

$jsonBody = $body | ConvertTo-Json

# Setting up Auth
$secpasswd = ConvertTo-SecureString $env:sec -AsPlainText -Force
$mycreds = New-Object System.Management.Automation.PSCredential("svc_jenkins", $secpasswd)

# Setting the Allowed Protocols
$AllProtocols = [System.Net.SecurityProtocolType]'Ssl3,Tls,Tls11,Tls12'
[System.Net.ServicePointManager]::SecurityProtocol = $AllProtocols
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $True }



function AD-Task-Update($ENVField, $HOSTField, $userIDField, $domainNameField, $accessServersField, $reqIDField) {
 #AD Team adds current logic and sub in the variable as required
 $scriptStr = "" #Insert PS-AD command as needed
 $scriptBlock = [scriptblock]::Create($scriptStr)
 
 #TODO: Figure out the syntax w/ AD Team 
 #Invoke-Command -ComputerName $server -ScriptBlock $scriptBlock -> Run the command with the PS-Ad


 #Update-Snow-Ticket # May need to update function definition depending on the output of AD PS logic, ie. what is needed to update the ticket?
 
}



function Update-Snow-Ticket {
Write-Output $("The value of ProjectKeyField  is: $ProjectKeyField " + "The value of AccessTypeField is: $AccessTypeField " + "The value of userIDField is: $userIDField " + "The value of domainNameField is: $domainNameField " + "The value of accessServersField is: $accessServersField " + "The value of reqIDField is: $reqIDField ")

#Invoking the POST
Invoke-RestMethod -Uri $devUri -Method 3 -Credential $mycreds -Body $jsonBody -ContentType "application/json" #Make the rest call to update the ticket
}

#TODO: Modify the function definition based on the POST from SNOW
#AD-Task-Update $ProjectKeyField $AccessTypeField $userIDField $domainNameField $accessServersField


#Initial testing
Update-Snow-Ticket



#For Debugging in Jenkins
<#
Write-Output $("The value of ENVField  is: $ENVField " + "The value of HOSTField is: $HOSTField " + 
"The value of userIDField is: $userIDField " + "The value of domainNameField is: $domainNameField" +
"The value of accessServersField is: $accessServersField" + "The values of header is: $jsonHeader" + "The values of body is: $jsonBody" + "The values of url is: $devUri")
#>