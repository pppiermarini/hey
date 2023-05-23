#!/bin/bash

# Functions

function send_normal_change_scheduled_email() {

    # Parse inputs
    local change_number=description=short_description=current_state=sys_id=date_updated=title=sub_title=priority=category=sub_category
    local author=owner=implementer=implementing_group=req_impl_start=req_impl_end=card_file_contents=data=subject
    local change_data="$1"
    change_number=$(echo "$change_data" | jq -r '.result.number.value')
    description=$(echo "$change_data" | jq -r '.result.description.value')
    short_description=$(echo "$change_data" | jq -r '.result.short_description.value')
    current_state=$(echo "$change_data" | jq -r '.result.state.display_value')
    sys_id=$(echo "$change_data" | jq -r '.result.sys_id.value')
    date_updated=$(echo "$change_data" | jq -r '.result.sys_updated_on.display_value')
    title="Scheduled: Normal Change Request $change_number -- $short_description"
    subject="Scheduled: Normal Change Request $change_number for $4"
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

    # Read the appropriate email template and set values
    card_file_contents="$(cat ./emails/normal_change_scheduled_email.json)"
    data=$(echo "$card_file_contents" \
            | jq --arg a "$title" '.title = $a' \
            | jq --arg a "$subject" '.summary = $a' \
            | jq --arg a "[$change_number]($view_change_url)" '.sections[0].facts[0].value = $a' \
            | jq --arg a "$short_description" '.sections[0].facts[1].value = $a' \
            | jq --arg a "$priority" '.sections[0].facts[2].value = $a' \
            | jq --arg a "$category > $sub_category" '.sections[0].facts[3].value = $a' \
            | jq --arg a "$author" '.sections[0].facts[4].value = $a' \
            | jq --arg a "$owner" '.sections[0].facts[5].value = $a' \
            | jq --arg a "$implementer" '.sections[0].facts[6].value = $a' \
            | jq --arg a "$implementing_group" '.sections[0].facts[7].value = $a' \
            | jq --arg a "$req_impl_start" '.sections[0].facts[11].value = $a' \
            | jq --arg a "$req_impl_end" '.sections[0].facts[12].value = $a' \
            | jq --arg a "$sub_title" '.sections[0].activityText = $a' \
            | jq --arg a "Scheduled at $date_updated" '.sections[0].activitySubtitle = $a' \
            | jq --arg a "$view_change_url" '.potentialAction[0].targets[0].uri = $a' \
            | jq --arg a "$view_change_xml_url" '.potentialAction[1].targets[0].uri = $a' \
            | jq --arg a "$description" '.sections[1].text = $a')

    # Invoke SNOW API to post a webhook
    curl --silent --request POST \
       --url "$2" \
       --header "Content-Type: application/json" \
       --data "$data" \
       --output /dev/null
}

function send_normal_change_closed_email() {
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
    local subject="Closed: Normal Change Request $change_number for $4"
    local sub_title="Normal Change [$change_number] with sys_id [$sys_id] updated and is in [$current_state] state"
    local view_change_url="$3$sys_id"
    local view_change_xml_url="$view_change_url&XML="

    # Read the appropriate notification template and set values
    card_file_contents="$(cat ./emails/normal_change_closed_email.json)"
    data=$(echo "$card_file_contents" \
            | jq --arg a "$title" '.title = $a' \
            | jq --arg a "$subject" '.summary = $a' \
            | jq --arg a "[$change_number]($view_change_url)" '.sections[0].facts[0].value = $a' \
            | jq --arg a "$short_description" '.sections[0].facts[1].value = $a' \
            | jq --arg a "$work_start" '.sections[0].facts[2].value = $a' \
            | jq --arg a "$work_end" '.sections[0].facts[3].value = $a' \
            | jq --arg a "$close_code" '.sections[0].facts[4].value = $a' \
            | jq --arg a "$sub_title" '.sections[0].activityText = $a' \
            | jq --arg a "Closed at $date_updated" '.sections[0].activitySubtitle = $a' \
            | jq --arg a "$close_notes" '.sections[1].text = $a' \
            | jq --arg a "$view_change_url" '.potentialAction[0].targets[0].uri = $a' \
            | jq --arg a "$view_change_xml_url" '.potentialAction[1].targets[0].uri = $a')


    # Invoke SNOW API to post a webhook
    curl --silent --request POST \
       --url "$2" \
       --header "Content-Type: application/json" \
       --data "$data" \
       --output /dev/null
}
