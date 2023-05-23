#!/bin/bash

# Functions

function send_create_normal_change_notification() {
    # Parse inputs
    local change_number=description=short_description=current_state=sys_id=date_created=title=sub_title=priority=category=sub_category
    local author=owner=implementer=implementing_group=req_impl_start=req_impl_end=card_file_contents=data
    local change_data="$1"
    change_number=$(echo "$change_data" | jq -r '.result.number.value')
    description=$(echo "$change_data" | jq -r '.result.description.value')
    short_description=$(echo "$change_data" | jq -r '.result.short_description.value')
    current_state=$(echo "$change_data" | jq -r '.result.state.display_value')
    sys_id=$(echo "$change_data" | jq -r '.result.sys_id.value')
    date_created=$(echo "$change_data" | jq -r '.result.sys_created_on.display_value')
    title="Created: Normal Change Request $change_number -- $short_description"
    sub_title="Normal Change [$change_number] with sys_id [$sys_id] created and is in [$current_state] state"
    priority=$(echo "$change_data" | jq -r '.result.priority.display_value')
    category=$(echo "$change_data" | jq -r '.result.category.display_value')
    sub_category=$(echo "$change_data" | jq -r '.result.u_sub_category.display_value')
    author=$(echo "$change_data" | jq -r '.result.u_change_author.display_value')
    owner=$(echo "$change_data" | jq -r '.result.u_change_author_manager.display_value')
    implementer=$(echo "$change_data" | jq -r '.result.assigned_to.display_value')
    implementing_group=$(echo "$change_data" | jq -r '.result.assignment_group.display_value')
    req_impl_start=$(echo "$change_data" | jq -r '.result.u_req_imp_start_date.display_value')
    req_impl_end=$(echo "$change_data" | jq -r '.result.u_req_imp_end_date.display_value')
    local view_change_url="$3$sys_id"
    local view_change_xml_url="$view_change_url&XML="


    # Read the appropriate notification template and set values
    card_file_contents="$(cat ./cards/create_normal_change_notification.json)"
    data=$(echo "$card_file_contents" \
            | jq --arg a "$title" '.attachments[0].content.body[0].text = $a' \
            | jq --arg a "[$change_number]($view_change_url)" '.attachments[0].content.body[3].facts[0].value = $a' \
            | jq --arg a "$short_description" '.attachments[0].content.body[3].facts[1].value = $a' \
            | jq --arg a "$priority" '.attachments[0].content.body[3].facts[2].value = $a' \
            | jq --arg a "$category > $sub_category" '.attachments[0].content.body[3].facts[3].value = $a' \
            | jq --arg a "$author" '.attachments[0].content.body[3].facts[4].value = $a' \
            | jq --arg a "$owner" '.attachments[0].content.body[3].facts[5].value = $a' \
            | jq --arg a "$implementer" '.attachments[0].content.body[3].facts[6].value = $a' \
            | jq --arg a "$implementing_group" '.attachments[0].content.body[3].facts[7].value = $a' \
            | jq --arg a "$req_impl_start" '.attachments[0].content.body[3].facts[10].value = $a' \
            | jq --arg a "$req_impl_end" '.attachments[0].content.body[3].facts[11].value = $a' \
            | jq --arg a "$sub_title" '.attachments[0].content.body[1].columns[1].items[0].text = $a' \
            | jq --arg a "Created at $date_created" '.attachments[0].content.body[1].columns[1].items[1].text = $a' \
            | jq --arg a "$view_change_url" '.attachments[0].content.actions[0].url = $a' \
            | jq --arg a "$view_change_xml_url" '.attachments[0].content.actions[1].url = $a' \
            | jq --arg a "$description" '.attachments[0].content.body[5].text = $a')
    
    # One more way to populate data in a json template file. Kept commented for future reference 
    #local card_file_contents="$(jq --arg a "$sub_title" '.attachments[0].content.body[1].columns[1].items[0].text = $a' ./cards/create_normal_change_notification.json)"
       
    # Invoke SNOW API to post a webhook
    curl --silent --request POST \
       --url "$2" \
       --header "Content-Type: application/json" \
       --data "$data" \
       --output /dev/null
}

function send_update_normal_change_notification() {
    # Parse inputs
    local change_number=short_description=current_state=sys_id=date_updated=card_file_contents=data
    local change_data="$1"
    change_number=$(echo "$change_data" | jq -r '.result.number.value')
    short_description=$(echo "$change_data" | jq -r '.result.short_description.value')
    current_state=$(echo "$change_data" | jq -r '.result.state.display_value')
    sys_id=$(echo "$change_data" | jq -r '.result.sys_id.value')
    date_updated=$(echo "$change_data" | jq -r '.result.sys_updated_on.display_value')
    local title="$current_state: Normal Change Request $change_number -- $short_description"
    local sub_title="Normal Change [$change_number] with sys_id [$sys_id] updated and is in [$current_state] state"
    local view_change_url="$3$sys_id"
    local view_change_xml_url="$view_change_url&XML="

    # Read the appropriate notification template and set values
    card_file_contents="$(cat ./cards/update_normal_change_notification.json)"
    data=$(echo "$card_file_contents" \
            | jq --arg a "$title" '.attachments[0].content.body[0].text = $a' \
            | jq --arg a "[$change_number]($view_change_url)" '.attachments[0].content.body[3].facts[0].value = $a' \
            | jq --arg a "$short_description" '.attachments[0].content.body[3].facts[1].value = $a' \
            | jq --arg a "$sub_title" '.attachments[0].content.body[1].columns[1].items[0].text = $a' \
            | jq --arg a "Updated at $date_updated" '.attachments[0].content.body[1].columns[1].items[1].text = $a' \
            | jq --arg a "$view_change_url" '.attachments[0].content.actions[0].url = $a' \
            | jq --arg a "$view_change_xml_url" '.attachments[0].content.actions[1].url = $a')
     
    # Invoke SNOW API to post a webhook
    curl --silent --request POST \
       --url "$2" \
       --header "Content-Type: application/json" \
       --data "$data" \
       --output /dev/null
}

function send_upload_attachments_notification() {
    # Parse inputs
    local change_number=short_description=current_state=sys_id=date_updated=att_data=card_file_contents=data
    local change_data="$1"
    local attachment_data="$5"
    change_number=$(echo "$change_data" | jq -r '.result.number.value')
    short_description=$(echo "$change_data" | jq -r '.result.short_description.value')
    current_state=$(echo "$change_data" | jq -r '.result.state.display_value')
    sys_id=$(echo "$change_data" | jq -r '.result.sys_id.value')
    date_updated=$(echo "$change_data" | jq -r '.result.sys_updated_on.display_value')
    local title="Attachments Uploaded: Normal Change Request $change_number -- $short_description"
    local sub_title="Normal Change [$change_number] with sys_id [$sys_id] updated and is in [$current_state] state"
    local view_change_url="$3$sys_id"
    local view_change_xml_url="$view_change_url&XML="
    local attachments="- "

    # Get all attachment data
    att_data=$(echo "$attachment_data" | jq '.result[] | ("[" + .file_name + "]('$4'" + .sys_id + ")\r- ")')
    att_data=$(echo $att_data | sed 's/" "//g' | sed 's/\\r/\r/g')
    length=${#att_data}
    att_data=${att_data:1:(length - 5)}
    attachments+=$att_data

    # Read the appropriate notification template and set values
    card_file_contents="$(cat ./cards/upload_attachments_notification.json)"
    data=$(echo "$card_file_contents" \
            | jq --arg a "$title" '.attachments[0].content.body[0].text = $a' \
            | jq --arg a "[$change_number]($view_change_url)" '.attachments[0].content.body[3].facts[0].value = $a' \
            | jq --arg a "$short_description" '.attachments[0].content.body[3].facts[1].value = $a' \
            | jq --arg a "$sub_title" '.attachments[0].content.body[1].columns[1].items[0].text = $a' \
            | jq --arg a "Updated at $date_updated" '.attachments[0].content.body[1].columns[1].items[1].text = $a' \
            | jq --arg a "$attachments" '.attachments[0].content.body[5].text = $a' \
            | jq --arg a "$view_change_url" '.attachments[0].content.actions[0].url = $a' \
            | jq --arg a "$view_change_xml_url" '.attachments[0].content.actions[1].url = $a')
     
    # Invoke SNOW API to post a webhook
    curl --silent --request POST \
       --url "$2" \
       --header "Content-Type: application/json" \
       --data "$data" \
       --output /dev/null
}

function send_copy_risk_assessment_notification() {
    # Parse inputs
    local change_number=short_description=current_state=sys_id=date_updated=card_file_contents=data
    local change_data="$1"
    change_number=$(echo "$change_data" | jq -r '.result.number.value')
    short_description=$(echo "$change_data" | jq -r '.result.short_description.value')
    current_state=$(echo "$change_data" | jq -r '.result.state.display_value')
    sys_id=$(echo "$change_data" | jq -r '.result.sys_id.value')
    date_updated=$(echo "$change_data" | jq -r '.result.sys_updated_on.display_value')
    local title="Risk Assesment Copied: Normal Change Request $change_number -- $short_description"
    local sub_title="Normal Change [$change_number] with sys_id [$sys_id] updated and is in [$current_state] state"
    local view_change_url="$3$sys_id"
    local view_change_xml_url="$view_change_url&XML="

    # Read the appropriate notification template and set values
    card_file_contents="$(cat ./cards/copy_risk_assessment_notification.json)"
    data=$(echo "$card_file_contents" \
            | jq --arg a "$title" '.attachments[0].content.body[0].text = $a' \
            | jq --arg a "[$change_number]($view_change_url)" '.attachments[0].content.body[3].facts[0].value = $a' \
            | jq --arg a "$short_description" '.attachments[0].content.body[3].facts[1].value = $a' \
            | jq --arg a "$sub_title" '.attachments[0].content.body[1].columns[1].items[0].text = $a' \
            | jq --arg a "Updated at $date_updated" '.attachments[0].content.body[1].columns[1].items[1].text = $a' \
            | jq --arg a "$view_change_url" '.attachments[0].content.actions[0].url = $a' \
            | jq --arg a "$view_change_xml_url" '.attachments[0].content.actions[1].url = $a')
     
    # Invoke SNOW API to post a webhook
    curl --silent --request POST \
       --url "$2" \
       --header "Content-Type: application/json" \
       --data "$data" \
       --output /dev/null
}

function send_approve_all_notification() {
    # Parse inputs
    local change_number=short_description=current_state=sys_id=date_updated=card_file_contents=data
    local change_data="$1"
    change_number=$(echo "$change_data" | jq -r '.result.number.value')
    short_description=$(echo "$change_data" | jq -r '.result.short_description.value')
    current_state=$(echo "$change_data" | jq -r '.result.state.display_value')
    sys_id=$(echo "$change_data" | jq -r '.result.sys_id.value')
    date_updated=$(echo "$change_data" | jq -r '.result.sys_updated_on.display_value')
    local title="Auto Approve All: Normal Change Request $change_number -- $short_description"
    local sub_title="Normal Change [$change_number] with sys_id [$sys_id] updated and is in [$current_state] state"
    local view_change_url="$3$sys_id"
    local view_change_xml_url="$view_change_url&XML="

    # Read the appropriate notification template and set values
    card_file_contents="$(cat ./cards/approve_all_notification.json)"
    data=$(echo "$card_file_contents" \
            | jq --arg a "$title" '.attachments[0].content.body[0].text = $a' \
            | jq --arg a "[$change_number]($view_change_url)" '.attachments[0].content.body[3].facts[0].value = $a' \
            | jq --arg a "$short_description" '.attachments[0].content.body[3].facts[1].value = $a' \
            | jq --arg a "$sub_title" '.attachments[0].content.body[1].columns[1].items[0].text = $a' \
            | jq --arg a "Updated at $date_updated" '.attachments[0].content.body[1].columns[1].items[1].text = $a' \
            | jq --arg a "$view_change_url" '.attachments[0].content.actions[0].url = $a' \
            | jq --arg a "$view_change_xml_url" '.attachments[0].content.actions[1].url = $a')
     
    # Invoke SNOW API to post a webhook
    curl --silent --request POST \
       --url "$2" \
       --header "Content-Type: application/json" \
       --data "$data" \
       --output /dev/null
}

function send_approve_change_management_notification() {
    # Parse inputs
    local change_number=short_description=current_state=sys_id=date_updated=card_file_contents=data
    local change_data="$1"
    change_number=$(echo "$change_data" | jq -r '.result.number.value')
    short_description=$(echo "$change_data" | jq -r '.result.short_description.value')
    current_state=$(echo "$change_data" | jq -r '.result.state.display_value')
    sys_id=$(echo "$change_data" | jq -r '.result.sys_id.value')
    date_updated=$(echo "$change_data" | jq -r '.result.sys_updated_on.display_value')
    local title="Change Management Approval: Normal Change Request $change_number -- $short_description"
    local sub_title="Normal Change [$change_number] with sys_id [$sys_id] updated and is in [$current_state] state"
    local view_change_url="$3$sys_id"
    local view_change_xml_url="$view_change_url&XML="

    # Read the appropriate notification template and set values
    card_file_contents="$(cat ./cards/approve_change_management_notification.json)"
    data=$(echo "$card_file_contents" \
            | jq --arg a "$title" '.attachments[0].content.body[0].text = $a' \
            | jq --arg a "[$change_number]($view_change_url)" '.attachments[0].content.body[3].facts[0].value = $a' \
            | jq --arg a "$short_description" '.attachments[0].content.body[3].facts[1].value = $a' \
            | jq --arg a "$sub_title" '.attachments[0].content.body[1].columns[1].items[0].text = $a' \
            | jq --arg a "Updated at $date_updated" '.attachments[0].content.body[1].columns[1].items[1].text = $a' \
            | jq --arg a "$view_change_url" '.attachments[0].content.actions[0].url = $a' \
            | jq --arg a "$view_change_xml_url" '.attachments[0].content.actions[1].url = $a')
     
    # Invoke SNOW API to post a webhook
    curl --silent --request POST \
       --url "$2" \
       --header "Content-Type: application/json" \
       --data "$data" \
       --output /dev/null
}

function send_deployment_complete_notification() {
    # Parse inputs
    local change_number=short_description=current_state=sys_id=date_updated=work_start=work_end=close_code=card_file_contents=data
    local change_data="$1"
    change_number=$(echo "$change_data" | jq -r '.result.number.value')
    short_description=$(echo "$change_data" | jq -r '.result.short_description.value')
    current_state=$(echo "$change_data" | jq -r '.result.state.display_value')
    sys_id=$(echo "$change_data" | jq -r '.result.sys_id.value')
    date_updated=$(echo "$change_data" | jq -r '.result.sys_updated_on.display_value')
    work_start=$(echo "$change_data" | jq -r '.result.work_start.display_value')
    work_end=$(echo "$change_data" | jq -r '.result.work_end.display_value')
    close_code=$(echo "$change_data" | jq -r '.result.u_close_code.display_value')
    local title="Deployment Complete: Normal Change Request $change_number -- $short_description"
    local sub_title="Normal Change [$change_number] with sys_id [$sys_id] updated and is in [$current_state] state"
    local view_change_url="$3$sys_id"
    local view_change_xml_url="$view_change_url&XML="

    # Read the appropriate notification template and set values
    card_file_contents="$(cat ./cards/deployment_complete_notification.json)"
    data=$(echo "$card_file_contents" \
            | jq --arg a "$title" '.attachments[0].content.body[0].text = $a' \
            | jq --arg a "[$change_number]($view_change_url)" '.attachments[0].content.body[3].facts[0].value = $a' \
            | jq --arg a "$short_description" '.attachments[0].content.body[3].facts[1].value = $a' \
            | jq --arg a "$work_start" '.attachments[0].content.body[3].facts[2].value = $a' \
            | jq --arg a "$work_end" '.attachments[0].content.body[3].facts[3].value = $a' \
            | jq --arg a "$close_code" '.attachments[0].content.body[3].facts[4].value = $a' \
            | jq --arg a "$sub_title" '.attachments[0].content.body[1].columns[1].items[0].text = $a' \
            | jq --arg a "Updated at $date_updated" '.attachments[0].content.body[1].columns[1].items[1].text = $a' \
            | jq --arg a "$view_change_url" '.attachments[0].content.actions[0].url = $a' \
            | jq --arg a "$view_change_xml_url" '.attachments[0].content.actions[1].url = $a')
     
    # Invoke SNOW API to post a webhook
    curl --silent --request POST \
       --url "$2" \
       --header "Content-Type: application/json" \
       --data "$data" \
       --output /dev/null
}

function send_change_closed_notification() {
    # Parse inputs
    local change_number=short_description=current_state=sys_id=date_updated=work_start=work_end=close_code=close_notes=card_file_contents=data
    local change_data="$1"
    change_number=$(echo "$change_data" | jq -r '.result.number.value')
    short_description=$(echo "$change_data" | jq -r '.result.short_description.value')
    current_state=$(echo "$change_data" | jq -r '.result.state.display_value')
    sys_id=$(echo "$change_data" | jq -r '.result.sys_id.value')
    date_updated=$(echo "$change_data" | jq -r '.result.sys_updated_on.display_value')
    work_start=$(echo "$change_data" | jq -r '.result.work_start.display_value')
    work_end=$(echo "$change_data" | jq -r '.result.work_end.display_value')
    close_code=$(echo "$change_data" | jq -r '.result.u_close_code.display_value')
    close_notes=$(echo "$change_data" | jq -r '.result.close_notes.display_value')
    local title="Closed: Normal Change Request $change_number -- $short_description"
    local sub_title="Normal Change [$change_number] with sys_id [$sys_id] updated and is in [$current_state] state"
    local view_change_url="$3$sys_id"
    local view_change_xml_url="$view_change_url&XML="

    # Read the appropriate notification template and set values
    card_file_contents="$(cat ./cards/change_closed_notification.json)"
    data=$(echo "$card_file_contents" \
            | jq --arg a "$title" '.attachments[0].content.body[0].text = $a' \
            | jq --arg a "[$change_number]($view_change_url)" '.attachments[0].content.body[3].facts[0].value = $a' \
            | jq --arg a "$short_description" '.attachments[0].content.body[3].facts[1].value = $a' \
            | jq --arg a "$work_start" '.attachments[0].content.body[3].facts[2].value = $a' \
            | jq --arg a "$work_end" '.attachments[0].content.body[3].facts[3].value = $a' \
            | jq --arg a "$close_code" '.attachments[0].content.body[3].facts[4].value = $a' \
            | jq --arg a "$sub_title" '.attachments[0].content.body[1].columns[1].items[0].text = $a' \
            | jq --arg a "Updated at $date_updated" '.attachments[0].content.body[1].columns[1].items[1].text = $a' \
            | jq --arg a "$close_notes" '.attachments[0].content.body[5].text = $a' \
            | jq --arg a "$view_change_url" '.attachments[0].content.actions[0].url = $a' \
            | jq --arg a "$view_change_xml_url" '.attachments[0].content.actions[1].url = $a')
     
    # Invoke SNOW API to post a webhook
    curl --silent --request POST \
       --url "$2" \
       --header "Content-Type: application/json" \
       --data "$data" \
       --output /dev/null
}
