#!/usr/bin/env bash
#-------------------------------------------------------------------------------------------------------------------
# global variables
DAYS_TO_EXPIRATION_WINDOW=60
BEARER_TOKEN=""
DOWNLOAD_PASSWORD=""
FULL_CERT_LIST=""
COMMON_NAME=""
DOWNLOAD_FORMAT=""
DISABLED=""
OLD_IFS="$IFS" 
#
# For Testing only... setting this variable to conatins the name of a specfic certificate will result in only that specified cert being processed
# Example:
TEST_CERT="scm-automation-test.incomm.com"
#
# Usually it should be empty to allow teh script to process all certificates
##TEST_CERT=""
#
#-------------------------------------------------------------------------------------------------------------------
#constants
TODAY=`date +%F`
EXPIRING_CERTS_FILENAME="SCM_EXPIRING_CERTIFICATES.log"
RENEWED_CERTS_FILE="NO_TOUCH_RENEWED_CERTS.txt"
PICKEDUP_CERTS_FILE="NO_TOUCH_PICKEDUP_CERTS.txt"
RENEWED_CERTS_FILE_SCM="SCM_RENEWED_CERTS.log"
PICKEDUP_CERTS_FILE_SCM="SCM_PICKEDUP_CERTS.log"
PICKEDUP_CERTS_FILE_PATH="/app/jenkins/workspace/Cert-Automation-Pipeline-2"
BASE_VENAFI_URL="https://api.sslcerts.incomm.com/"
USE_CERT_ID_AS_PICKUP_LOCATION="USE_CERT_ID_AS_PICKUP_LOCATION"
PKCS12_FORMAT="pkcs12"
JKS_FORMAT="jks"
DEFAULT_PICKUP_FILE_FORMAT="$PKCS12_FORMAT"
VENAFI_URL="https://api.sslcerts.incomm.com/vedsdk"
#-------------------------------------------------------------------------------------------------------------------
# helper functions
# explain to user how to use teh script's para,eters properly
usage(){
    echo
    echo "Usage: $0 --bearer-token [--help ] "
    echo
    echo  "-t, --bearer-token        Bearer token used to authenticate with Venafi."
    echo "                            - this parameter is required"
    echo
    echo  "-p, --download-password   Password used for encryption/decryption when downloading certs from Venafi."
    echo "                            - this parameter is required"
    echo    
    echo  "-h, --help                Show this help text"
    echo
    #
    echo "Exiting the script"
    echo
    exit
}
#utility function - strips carriage returns from the passed in string
stripCarriageReturn() {
  local var="$1"
  ###echo -n "$var" |tr -d '\n'
  echo -n "$var" |tr '\n' ' '
}
#utility function - strips any passed in text from the passed in string
strip_passed_text() {
  local var="$1"
  local strip_this_text="$2"
  # if stripping spaces out, do that specifically... 
  if [[ -z "$strip_this_text" ]]; then
    echo -n "${var// }"
  # else use the standard process for all other text
  else
    echo -n "${var//$strip_this_text}"
  fi  
}
#utility function - strips double quotes from then passed in string
strip_double_quotes() {
  local var="$1"
  echo -n "${var//\"}"
}
#The curl command returns a cert-id string that is unusable.  This function fixes the issues, including turning double slashes (//) into sungle slashes (/)
fix_id_string(){
  local foo="$@"
  local lastchar=""
  local currchar=""
  local UPDATED_STRING="\""
  local thing=""
  for (( i=0; i<${#foo}; i++ )); do
     currchar="${foo:$i:1}"
     if  [[ "$currchar" == *\\* ]]  && [[ "$lastchar" == *\\* ]] ; then
       thing=""   
     else
       UPDATED_STRING="$UPDATED_STRING${foo:$i:1}"
     fi
     lastchar="${foo:$i:1}"
  done
  echo -n "$UPDATED_STRING"
}
# we currently only support two download formats JKS & PKCS12.  The default format currently is "pkcs12"
determine_pickup_format() {
  local CURR_CERT_DOWNLOAD_FORMAT="$1"
  if [[ "$CURR_CERT_DOWNLOAD_FORMAT" == "$JKS_FORMAT" ]]; then
    echo -n "$JKS_FORMAT"
  else   
    echo -n "$DEFAULT_PICKUP_FILE_FORMAT"
  fi  
}
# will pause the skip when called... mainly used for debugging.
paws(){
  echo
  read -n1 -r -s -p "Press any key to continue"
}
#checks to make sure all required paramters are present and correct
sanity_checks(){
   if [[ -z "$BEARER_TOKEN" ]]; then
     echo 
     echo "ERROR:  The required '--bearer-token' was not passed into this script"
     echo
     usage
   fi
   if [[ -z "$DOWNLOAD_PASSWORD" ]]; then
     echo 
     echo "ERROR:  The '--download-password' required for certificate download was not passed into this script"
     echo
     usage
   fi      
}
# performs directory cleanup functions when the script starts up
clean_up(){
  rm "$EXPIRING_CERTS_FILENAME" 2> /dev/null
  rm "$RENEWED_CERTS_FILE_SCM" 2> /dev/null
  rm "$PICKEDUP_CERTS_FILE_SCM" 2> /dev/null
  # recreate this file, as an empty file
  touch ${EXPIRING_CERTS_FILENAME}
}
# This method uses a curl command to get a list of all certs from venafi.  
# NOTE: This list includes both expiring & non-expiring certs
create_list_of_all_certs () {   
  curl -s -o out.json --request GET --url "$BASE_VENAFI_URL"vedsdk/Certificates/ --header "Content-Type:application/json" --header "Authorization:Bearer $BEARER_TOKEN"  
  FULL_CERT_LIST=`jq '.Certificates[] | "\(.Name),\(.X509.ValidFrom),\(.X509.ValidTo),\(._links[].Details),\(.DN)"' out.json`
}
# This method uses a curl command to get detail-level information about the current certificate being looked at
create_curr_cert_details () {   
  local CERT_DETAILS_PATH="$1"  
  curl -s -o details.json --request GET --url "$BASE_VENAFI_URL$CERT_DETAILS_PATH" --header "Content-Type:application/json" --header "Authorization:Bearer $BEARER_TOKEN" 
  COMMON_NAME=`jq '.CertificateDetails.CN' details.json`
  DOWNLOAD_FORMAT=`jq '.CustomFields[] | select(.Name=="Owner Tracking") | .Value[]' details.json`
  DISABLED=`jq '.Disabled' details.json`
  ## paws
}
# This method performs a certificate renewal.  It will renew the single certioficate that is specifically passed to it via that parameter 'CURR_CERT_NAME'.
renew_current_expiring_cert() {
    local CURR_CERT_NAME=""
    local CURR_CERT_TO_DATE=""
    local CURR_CERT_ID_TO_RENEW=""
    local CURR_CERT_DOWNLOAD_FORMAT=""       
    local CURR_CERT_DETAILS=""
    local CERT_NEEDS_RENEWAL=1 
    local CERT_RENEW_WORKED=1
    local CERT_PICKUP_FILENAME=""
    local UNIQUE_CERT_STRING=""
    local CERT_PICKUP_FILENAME_EXISTS_LOCALLY=""
    local CERT_PICKUP_LOCATION=""    
    #
    CURR_CERT_NAME="$1"
    shift
    CURR_CERT_TO_DATE="$1"
    shift
    CURR_CERT_DOWNLOAD_FORMAT="$1"
    shift    
    CURR_CERT_ID_TO_RENEW="$@"
    # 
    local UNIQUE_SEARCH_STRING="$CURR_CERT_NAME,$CURR_CERT_TO_DATE"
    #
    # we do not want to renew the same cert twice.... so check teh 'successfully renewed' file to see if this is in the file already
    # ensure file exists with 'touch'
    touch "$RENEWED_CERTS_FILE"
    touch "$RENEWED_CERTS_FILE_SCM"
    # search 'silently' for our current cert in the 'successfully renewed' file file 
    grep -q "$UNIQUE_SEARCH_STRING" "$RENEWED_CERTS_FILE"
    CERT_NEEDS_RENEWAL=$?
    #remove any quotes that wrap CURR_CERT_ID_TO_RENEW... venafi is super pickey here... and I actaully add them back manually below during the vert command
    local CERT_ID_TO_RENEW=$(strip_double_quotes "$CURR_CERT_ID_TO_RENEW") 
    #
    # If the above 'grep' fals, then we have not renewed this cert yet, so renew it
    if [[ "$CERT_NEEDS_RENEWAL" -ne 0 ]]; then
      #
      # strip out any embedded spaces
      CERT_PICKUP_FILENAME=$(strip_passed_text "$CURR_CERT_NAME" " ")
      # make filename unique by appending date
      CERT_PICKUP_FILENAME="$CERT_PICKUP_FILENAME-$CURR_CERT_TO_DATE.renew"       
      #
      # renew the cert
      #
      # uncomment to view teh command being used
      #echo "vcert86 renew  -u ${VENAFI_URL} -t ${BEARER_TOKEN} --id ${CERT_ID_TO_RENEW}  --pickup-id-file ${CERT_PICKUP_FILENAME} --no-prompt --no-pickup --csr service"
      # The actual CERT renewal occurs here!!!!
      vcert86 renew  -u ${VENAFI_URL} -t ${BEARER_TOKEN} --id "${CERT_ID_TO_RENEW}"  --pickup-id-file ${CERT_PICKUP_FILENAME} --no-prompt --no-pickup --csr service
      CERT_RENEW_WORKED=$?
      # If the above 'vcert renew' is successful, then record that we renewed teh cert 
      if [[ "$CERT_RENEW_WORKED" -eq 0 ]]; then
        # double-check for the pickup-files existance
        ls -la "$CERT_PICKUP_FILENAME" 2>/dev/null
        CERT_PICKUP_FILENAME_EXISTS_LOCALLY=$?
        #if cert pickup filename is found locally, then use that for the venafi pickup location!!!
        if [[ "$CERT_PICKUP_FILENAME_EXISTS_LOCALLY" -eq 0 ]]; then
          # echo the contents of teh pickup file into a variable
          CERT_PICKUP_LOCATION=`cat "$CERT_PICKUP_FILENAME"`
          #
          # if cert pickup file exists, but is empty... then tell script to use teh cert_id as pickup location
          if [[ -z "$CERT_PICKUP_LOCATION" ]]; then
            CERT_PICKUP_LOCATION="$USE_CERT_ID_AS_PICKUP_LOCATION"
          fi
          UNIQUE_CERT_STRING="$CURR_CERT_NAME,$CURR_CERT_TO_DATE,$CERT_PICKUP_LOCATION,$CURR_CERT_DOWNLOAD_FORMAT,$CERT_PICKUP_FILENAME,$CURR_CERT_ID_TO_RENEW"
        else  
          UNIQUE_CERT_STRING="$CURR_CERT_NAME,$CURR_CERT_TO_DATE,$USE_CERT_ID_AS_PICKUP_LOCATION,$CURR_CERT_DOWNLOAD_FORMAT,$CERT_PICKUP_FILENAME,$CURR_CERT_ID_TO_RENEW"
        fi                   
        echo "$UNIQUE_CERT_STRING" >> "$RENEWED_CERTS_FILE"
        echo "$UNIQUE_CERT_STRING" >> "$RENEWED_CERTS_FILE_SCM" 
      fi  # if cert_renew_worked  
    fi  # if cert-needs renewal
}
# This method:
#   - loops through the list of certs created by the method 'create_list_of_all_certs'
#   - if it determines a certificate will expire in the time window defined by 'DAYS_TO_EXPIRATION_WINDOW' 
#     - then it call the method 'renew_current_expiring_cert'
renew_all_expiring_certs(){ 
    local cert_name=""
    local cert_name_tmp=""    
    local cert_from_date=""
    local cert_to_date=""
    local cert_details=""     
    local cert_id_tmp=""  
    local cert_id="" 
    # The next command splits the list of all certs, by the newline character, into an array.  
    # Each array item member contain one certificate, on a single line, along with other realted info like expiration from & to dates etc.
    # Example:
    #   "spscmjenkins01v.incommtech.net,2017-08-25T13:04:08.0000000Z,2019-08-25T13:04:08.0000000Z,/vedsdk/certificates/%7b767f5104-88f7-41c9-8f89-0cad45ebb687%7d,\\VED\\Policy\\Certificates\\Standard\\Software Configuration\\spscmjenkins01v.incommtech.net"
    # 
    IFS=$'\n' read -rd '' -a tmp_certs_array <<<"$FULL_CERT_LIST"
    # reset IFS back to original setting after everytime we mess with it!
    IFS="$OLD_IFS"
    #
    # loop through the certificate array one cert at a time.
    for curr_cert in "${tmp_certs_array[@]}"; do
      #  echo "$curr_cert"
      #
      # break the current certificate into another array, breaking the current item by 'commas' 
      # Example
      #  spscmjenkins01v.incommtech.net
      #  2017-08-25T13:04:08.0000000Z
      #  2019-08-25T13:04:08.0000000Z"
      #  /vedsdk/certificates/%7b767f5104-88f7-41c9-8f89-0cad45ebb687%7d,
      # "\VED\Policy\Certificates\Standard\Software Configuration\spscmjenkins01v.incommtech.net"
      #
      # 
      IFS=',' read -ra cert_array <<< "$curr_cert"
      #readarray -d , -t cert_array <<< "$curr_cert"
      IFS="$OLD_IFS"
      #
      cert_name_tmp="${cert_array[0]:1:100}"
      cert_from_date="${cert_array[1]:0:10}"
      cert_to_date="${cert_array[2]:0:10}"
      cert_details="${cert_array[3]:0:200}"
      cert_id_tmp="${cert_array[4]:0:200}"
      #
      # uncomment the 'echo' commands below for help in debugging only
      # -----------------------------------------------       
      #  echo
      #  echo "1) cert_name: $cert_name_tmp"
      #  echo "2) cert_to_date: $cert_to_date"          
      #  echo "3) cert_id: $cert_id_tmp"
      #  echo "4) cert_details: $cert_details"  
      # ----------------------------------------------- 
      #
      #  Only try to renew a cert if either of these is true:
      #   -  it matches the cert name specified in "TEST_CERT" variable
      #   -  TEST_CERT variable is empty string
      #
      if [[ "$cert_name_tmp" = *"$TEST_CERT"*  ]]  || [[ -z "$TEST_CERT" ]]; then
        #
        # get rid of teh double slashes in the cert_id, venafi will probably barf if we pass teh double slashes to it.  Also remove any embedded carriage returns
        cert_id_tmp=$(fix_id_string "$cert_id_tmp")  
        cert_id=$(stripCarriageReturn "$cert_id_tmp")
        # look up the cert-details.  Currently we are most inetrested in getting the certificate 'common-name'
        # NOTE: This method populates the global variables: COMMON_NAME & DOWNLOAD_FORMAT
        create_curr_cert_details "$cert_details"
        #
        # If the current certificate is marked with '"Disabled": true', then do NOT process it, instead skip back up to the top of the 'for' loop and 
        # resume processing using the next certificate in our 'tmp_certs_array' array.
        #
        if [[ "$DISABLED" == "true" ]]; then
          continue
        fi
        #
        # If we can pull CommonName from the cert details always use that as the 'cert-name', as its very clean with no embedded spaces or illegal characters..
        # else take the contents of Name which is mstly good, but sometimes has crap characters like spaces embedded...
        #
        if [[ ! -z " $COMMON_NAME" ]]; then
          #remove any quotes that wrap common_name
          COMMON_NAME=$(strip_double_quotes "$COMMON_NAME") 
          cert_name="$COMMON_NAME"
        else  
          cert_name="$cert_name_tmp"      
        fi
        #
        if [[ ! -z " $DOWNLOAD_FORMAT" ]] && [[ " $DOWNLOAD_FORMAT"  = *"$JKS_FORMAT"* ]]; then
          DOWNLOAD_FORMAT="$JKS_FORMAT"
        else  
          DOWNLOAD_FORMAT="$DEFAULT_PICKUP_FILE_FORMAT"    
        fi
        #
        ## uncomment the 'echo' commands for help debugging only
        #   echo '------------------------------------------------'
        #   echo "cert_name: $cert_name"
        #   echo "cert_details: $cert_details"      
        #   echo "cert_id: $cert_id"
        #   echo "COMMON_NAME: $COMMON_NAME"
        #   echo '------------------------------------------------'
        #   echo
        #
        # break date into seperate year month & day variables to help validate it
        year=${cert_to_date:0:4}
        month=${cert_to_date:5:2}
        day=${cert_to_date:8:2}
        #
        ### echo "$year $month $day"
        #
        # number validation regex string
        re='^[0-9]+$'
        #
        # Validate year, month & day against regex
        if [[ $year =~ $re ]] && [[ $month =~ $re ]] && [[ $day =~ $re ]]; then
          # change the current certificate expiration date to 'days'
          d1=$(date --date="$cert_to_date" +%s)
          # change todays date to 'days'
          d2=$(date --date="$TODAY" +%s)
          # calculate the number of days until the current cert expires
          number_of_days_till_cert_expiration=$(( (d1 - d2) / 86400 ))
          # List all certs that have a number_of_days_till_cert_expiration that is smaller than our 60 day allowable window
          if [[ $number_of_days_till_cert_expiration -lt $DAYS_TO_EXPIRATION_WINDOW ]]; then
            # if cert expires in less than 60 days or is already expired, write it out into our file.
            echo "$cert_name,$cert_from_date,$cert_to_date,$DOWNLOAD_FORMAT,${cert_id}" >> "$EXPIRING_CERTS_FILENAME"
            #
            # call the renewal helper method passing it the current certificate
            renew_current_expiring_cert "$cert_name" "$cert_to_date" "$DOWNLOAD_FORMAT" "$cert_id"
          fi
        else 
          echo "ERROR: Certificate with name '$cert_name' does not contain a valid Expiration date.  Expected date, found: ${cert_array[2]}" 
        fi
      fi # if cert-name matches TEST_CERT, or TEST_CERT is an empty string
    done
}
# This method performs a certificate pickup.  It will picksup the single certioficate that is specifically passed to it via that parameter 'CURR_CERT_NAME'.
pickup_renewed_cert() {
    local CURR_CERT_NAME=""
    local CURR_CERT_TO_DATE=""
    local CURR_CERT_PICKUP_LOCATION=""
    local CURR_CERT_ID_TO_RENEW="" 
    local CURR_CERT_DOWNLOAD_FORMAT=""
    local CURR_CERT_PICKUP_FILENAME=""       
    local CERT_ALREADY_PICKED_UP=1
    local PICKUP_SUCCESS=1 
    local CERT_EXISTS_LOCALLY=1
    local CURR_PICKUP_FORMAT="$DEFAULT_PICKUP_FILE_FORMAT"
    local CERT_DOWNLOAD_FILENAME_TMP=""
    local CURR_CERT_PICKUP_FILE_LOCATION=""
    local CURR_CERT_PICKUP_STRING=""
    local CURR_CERT_ID_TO_PICKUP_TMP=""
    local DOWNLOAD_FILENAME=""
    #
    CURR_CERT_NAME="$1"
    shift
    CURR_CERT_TO_DATE="$1"
    shift
    CURR_CERT_PICKUP_LOCATION="$1"
    shift    
    CURR_CERT_DOWNLOAD_FORMAT="$1"
    shift     
    CURR_CERT_PICKUP_FILENAME="$1"
    shift
    CURR_CERT_ID_TO_PICKUP_TMP="$@"
    #
    CURR_CERT_ID_TO_PICKUP=$(stripCarriageReturn "$CURR_CERT_ID_TO_PICKUP_TMP")
    # 
    # strip out any embedded spaces from this string....
    local LOCAL_CERT_PICKUP_LOCATION=$(strip_passed_text "$CURR_CERT_PICKUP_LOCATION" " ")
    local UNIQUE_CERT_STRING="$CURR_CERT_NAME,$CURR_CERT_TO_DATE,$CURR_CERT_DOWNLOAD_FORMAT,$CURR_CERT_PICKUP_FILENAME,$LOCAL_CERT_PICKUP_LOCATION"    
    local UNIQUE_SEARCH_STRING="$CURR_CERT_NAME,$CURR_CERT_TO_DATE,$CURR_CERT_DOWNLOAD_FORMAT,$CURR_CERT_PICKUP_FILENAME"   
    #remove any quotes that wrap CURR_CERT_ID_TO_PICKUP... venafi is super pickey here... and I actaully add them back manually below
    local CERT_ID_TO_PICKUP=$(strip_double_quotes "$CURR_CERT_ID_TO_PICKUP") 
    local USE_PICKUP_FILE=""
    #
    touch "$PICKEDUP_CERTS_FILE"
    touch "$PICKEDUP_CERTS_FILE_SCM"
    grep -q "$UNIQUE_SEARCH_STRING" "$PICKEDUP_CERTS_FILE"
    CERT_ALREADY_PICKED_UP=$?
    #
    # if current cert NOT altready picked up
    if [[ "$CERT_ALREADY_PICKED_UP" -ne 0 ]]; then
      CERT_DOWNLOAD_FILENAME_TMP=$(strip_passed_text "$CURR_CERT_NAME" " ")
      #make filename unique by appending date
      CERT_DOWNLOAD_FILENAME="$CERT_DOWNLOAD_FILENAME_TMP-$CURR_CERT_TO_DATE"
      CURR_PICKUP_FORMAT=$(determine_pickup_format "$CURR_CERT_DOWNLOAD_FORMAT")
      #
      if [[ -f "$CURR_CERT_PICKUP_FILENAME" ]]; then
          CURR_CERT_PICKUP_FILE_LOCATION=`cat ${CURR_CERT_PICKUP_FILENAME}`
          #
          # if cert pickup file exists, and is NOT empty... then tell script to use the --pickup-id-file (CURR_CERT_PICKUP_FILENAME), else use the --pickup-id (CURR_CERT_ID_TO_PICKUP)
          if [[ ! -z "$CURR_CERT_PICKUP_FILE_LOCATION" ]]; then
            USE_PICKUP_FILE="Y"
          else
            USE_PICKUP_FILE="N"
          fi  
      else
        USE_PICKUP_FILE="N"
      fi
      # Uncomment next 3 lines for help debugging
      # WHOOP!!
      # echo "CURR_CERT_PICKUP_FILE_LOCATION: $CURR_CERT_PICKUP_FILE_LOCATION"
      # echo "CURR_CERT_PICKUP_FILENAME: $CURR_CERT_PICKUP_FILENAME"
      # echo "USE_PICKUP_FILE: $USE_PICKUP_FILE"
      # NOTE: All the --pickup-id and --pickup-if-file paramters used below require that they be wrapped in double quotes (""), like is shown below.  Do not change that!!
      #
      # # The actual CERT pickup occurs inside the section below!!!!
      if [[ "$CURR_PICKUP_FORMAT" == "$PKCS12_FORMAT" ]]; then
        DOWNLOAD_FILENAME="$PICKEDUP_CERTS_FILE_PATH/$CERT_DOWNLOAD_FILENAME.pfx"
        #
        if [[ "$USE_PICKUP_FILE" != "Y" ]]; then
          vcert86 pickup -u ${VENAFI_URL} -t ${BEARER_TOKEN}  --file ${DOWNLOAD_FILENAME}  --pickup-id "${CERT_ID_TO_PICKUP}" --format ${PKCS12_FORMAT} --no-prompt  --key-password ${DOWNLOAD_PASSWORD} --timeout 30 
        else
          vcert86 pickup -u ${VENAFI_URL} -t ${BEARER_TOKEN}  --file ${DOWNLOAD_FILENAME}  --pickup-id-file "${CURR_CERT_PICKUP_FILENAME}" --format ${PKCS12_FORMAT} --no-prompt  --key-password ${DOWNLOAD_PASSWORD} --timeout 30 
        fi
        PICKUP_SUCCESS=$?              
      elif [[ "$CURR_PICKUP_FORMAT" == "$JKS_FORMAT" ]]; then
        DOWNLOAD_FILENAME="$PICKEDUP_CERTS_FILE_PATH/$CERT_DOWNLOAD_FILENAME.jks"
        #
        if [[ "$USE_PICKUP_FILE" != "Y" ]]; then
          vcert86 pickup -u ${VENAFI_URL} -t ${BEARER_TOKEN}  --file ${DOWNLOAD_FILENAME}  --pickup-id "${CERT_ID_TO_PICKUP}" --format ${JKS_FORMAT} --no-prompt --key-password ${DOWNLOAD_PASSWORD} --jks-alias "$CURR_CERT_NAME" --timeout 30 
        else
          vcert86 pickup -u ${VENAFI_URL} -t ${BEARER_TOKEN}  --file ${DOWNLOAD_FILENAME}  --pickup-id-file "${CURR_CERT_PICKUP_FILENAME}" --format ${JKS_FORMAT} --no-prompt --key-password ${DOWNLOAD_PASSWORD} --jks-alias "$CURR_CERT_NAME"  --timeout 30 
        fi
        PICKUP_SUCCESS=$?                           
      else
        echo "ERROR: Unknown pickup file format '$CURR_PICKUP_FORMAT' calculated for Certificate '$CURR_CERT_NAME' located at '$CURR_CERT_ID_TO_RENEW'" 
        PICKUP_SUCCESS=1              
      fi 
      # If the above 'vcert86 pickup' failed, then we have not picked this cert yet.  Only record pickups if successful.
      # This check filters for only pickup 'success'
      if [[ "$PICKUP_SUCCESS" -eq 0 ]]; then
         # double-check for the files existance
         ls -la "$DOWNLOAD_FILENAME" > /dev/null 2>&1
         CERT_EXISTS_LOCALLY=$?
         #if cert is found locally, then success!!!
         if [[ "$CERT_EXISTS_LOCALLY" -eq 0 ]]; then
           #record the successfull download
           echo "$UNIQUE_CERT_STRING" >> "$PICKEDUP_CERTS_FILE"
           echo "$UNIQUE_CERT_STRING" >> "$PICKEDUP_CERTS_FILE_SCM"          
         fi
      fi   
    fi
}
# 
# This method:
#   - loops through the  renewed certs logfile '$RENEWED_CERTS_FILE'
#   - if it determines the certificate has not already beenpicked up 
#     - then it call the method 'pickup_renewed_cert'
# 
pickup_all_renewed_certs() {
    local pickup_cert_array
    local cert_name=""
    local cert_to_date=""
    local cert_pickup_location=""
    local cert_download_format=""
    local cert_pickup_filename=""
    local cert_id=""
    # if $RENEWED_CERTS_FILE exists....
    if [[ -f "$RENEWED_CERTS_FILE" ]]; then
      # loop through $RENEWED_CERTS_FILE, one line at at time 
      while read -r curr_cert; do 
        # break each line up into an array of cert_name, cert_to_date & cert-Id
        IFS=',' read -ra pickup_cert_array <<< "$curr_cert"
        #readarray -d , -t pickup_cert_array <<< "$curr_cert"      
        IFS="$OLD_IFS"
        cert_name="${pickup_cert_array[0]}"
        cert_to_date="${pickup_cert_array[1]}"
        cert_pickup_location="${pickup_cert_array[2]}"
        cert_download_format="${pickup_cert_array[3]}"
        cert_pickup_filename="${pickup_cert_array[4]}"
        cert_id="${pickup_cert_array[5]}"      
        #
        # uncomment the 'echo' commands below for help in debugging only
        # ----------------------------------------------- 
        #  echo "cert_name: $cert_name"
        #  echo "ccert_to_date: $cert_to_date"          
        #  echo "cert_id: $cert_id"
        #  echo "cert_download_format: $cert_download_format"
        # ----------------------------------------------- 
        #
        # call the pickup helper method passing it the current certificate
        pickup_renewed_cert "$cert_name" "$cert_to_date" "$cert_pickup_location" "$cert_download_format" "$cert_pickup_filename" "$cert_id"
      done < "$RENEWED_CERTS_FILE"
    fi
}
#------------------------------------ main script starts here -----------------------------
while [[ "$1" != "" ]]; do
    case $1 in
        -h | --help )           usage
                                exit
                                ;;
        -t | --bearer-token )   shift
                                BEARER_TOKEN="$1"
                                ;;
        -p | --download-password )
                                shift
                                DOWNLOAD_PASSWORD="$1"
                                ;;                                
        * )                     echo
                                echo "Errors found in formatting of passed parameters or unknown parameters used.  See usage example below:"
                                echo
                                usage
                                exit 1
                                ;;
    esac
    shift
done
#
OLD_IFS="$IFS"
#
TIME_NOW=`date`
echo "Script ${0} starts at ${TIME_NOW}"
sanity_checks
clean_up
create_list_of_all_certs
renew_all_expiring_certs
pickup_all_renewed_certs
#
IFS="$OLD_IFS"
#
echo;echo
echo "NOTE: A list of your expiring certificates can be found in the file '$EXPIRING_CERTS_FILENAME', in this directory"
echo
TIME_NOW=`date`
echo "Script ${0} completes at ${TIME_NOW}"
echo
