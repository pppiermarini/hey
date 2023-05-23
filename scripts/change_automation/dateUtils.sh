#!/bin/bash

#--------------------------------------------------------------------------------
# Contains many reusable functions for date validations and formatting
# Example Usage of the utility functions
#--------------------------------------------------------------------------------  
#
# s0=$(is_date_valid "abc")
# echo "$s0 date validity"
#
# s1=$(get_formatted_date "Sat Mar 20 02:02:57 EDT 2021")
# echo "$s1 formatted date"
#
# s2=$(get_formatted_utc_date "Sat Mar 20 02:02:57 EDT 2021")
# echo "$s2 formatted UTC date"
#
# s3=$(get_formatted_utc_date "Sat Mar 20 00:00:00 EDT 2021")
# echo "$s3 formatted UTC date"
#
# s4=$(get_current_formatted_date)
# echo "$s4 current date"
#
# s5=$(get_current_formatted_utc_date)
# echo "$s5 current UTC date"
#
# s6=$(end_time_greater_than_start_time "2021-03-20 00:12:23" "2021-03-21 03:32:34")
# echo "$s6 date range check"
#
# s7=$(get_utc_datetime "2021-03-20 00:31:23")
# echo "$s7"
#--------------------------------------------------------------------------------

function is_date_valid() {
    if ! date "+%Y-%m-%d %H:%M:%S" -d "$@" > /dev/null  2>&1; then
        # return false
        echo "1"
    else
        # return true
        echo "0"
    fi
}

function get_formatted_date() {
    date "+%Y-%m-%d %H:%M:%S" -d "$@"
}

function get_formatted_utc_date() {
    TZ='UTC' date "+%Y-%m-%d %H:%M:%S" -d "$@"
}

function get_formatted_eastern_date() {
    TZ='US/Eastern' date "+%Y-%m-%d %H:%M:%S" -d "$@"
}

function get_current_formatted_date() {
    date "+%Y-%m-%d %H:%M:%S"
}

function get_current_formatted_utc_date() {
    TZ='UTC' date "+%Y-%m-%d %H:%M:%S"
}

function get_current_formatted_eastern_date() {
    TZ='US/Eastern' date "+%Y-%m-%d %H:%M:%S"
}

function get_eastern_datetime() {
   TZ='US/Eastern' date --date=@$(date "+%s" --date="$1")
}

function get_utc_datetime() {
  date -u --date=@$(date "+%s" --date="$1")
}

function convert_to_utc() {
  TZ='UTC' date "+%Y-%m-%d %H:%M:%S %Z" -d "$@"
}

function end_time_greater_than_start_time() {
    local start=$1
    local end=$2
    local startDtValid=endDtValid=startDt=endDt=
    startDtValid=$(is_date_valid "$start")
    endDtValid=$(is_date_valid "$end")
    if [[ "$startDtValid" == 1 || "$endDtValid" == 1 ]]; then
        echo "1"
    else
        startDt=$(date "+%s" -d "$start")
        endDt=$(date "+%s" -d "$end")
        if [[ $startDt -ge $endDt ]]; then
            echo "1"
        else
            echo "0"
        fi        
    fi
}
