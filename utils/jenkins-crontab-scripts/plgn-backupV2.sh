#!/bin/bash
#
now=$(date +"%Y%m%d-%H%M")
cd /app/jenkins
find plugins/ -name "*.*pi" -exec tar -rf /app/jenkins_backup/${now}_plugins.tar {} \;
find /app/jenkins_backup -mtime +5 -name '*plugins.tar' -exec rm {} \;
sleep 5
scp -i /home/tcserver/.ssh/id_backup /app/jenkins_backup/${now}_plugins.tar root@10.40.6.203:/app/backup_archive
ssh -i /home/tcserver/.ssh/id_backup -q -o StrictHostKeyChecking=no root@10.40.6.203 'find /app/backup_archive -mtime +5 -name '*plugins.tar' -exec rm {} \;'