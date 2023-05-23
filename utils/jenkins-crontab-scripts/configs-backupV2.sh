#!/bin/bash
#
now=$(date +"%Y%m%d-%H%M")
cd /app/jenkins
find . -maxdepth 1 -name "*.xml" -exec /bin/tar -rf /app/jenkins_backup/${now}_configs.tar {} \;
find nodes/ -name "*.*" -exec tar -rf /app/jenkins_backup/${now}_configs.tar {} \;
find secrets/ -name "*.*" -exec tar -rf /app/jenkins_backup/${now}_configs.tar {} \;
find jobs/ -name "config.xml" -not -path "*/builds*" -not -path "*/modules*" -exec tar -rf /app/jenkins_backup/${now}_configs.tar {} \;
find /app/jenkins_backup -mtime +5 -name '*configs.tar' -exec rm {} \;
sleep 5
scp -i /home/tcserver/.ssh/id_backup /app/jenkins_backup/${now}_configs.tar root@10.40.6.203:/app/backup_archive
ssh -i /home/tcserver/.ssh/id_backup -q -o StrictHostKeyChecking=no root@10.40.6.203 'find /app/backup_archive -mtime +5 -name '*configs.tar' -exec rm {} \;'