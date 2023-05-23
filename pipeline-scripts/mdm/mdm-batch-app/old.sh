#!/bin/sh

echo " "
echo "Begin deployment to ${HOSTNAME}"
echo "With the following settings"
echo "Hostname= ${HOSTNAME}"
echo "RELEASE= ${RELEASE}"
echo "ConfigBranch= ${CONFIG_BRANCH}"
echo "Skip Backup= ${SKIP_BACKUP}"
echo "whoami= " whoami

echo " "
echo " "
echo "clean up backups older than 40 days..."
ssh -i /app/jenkins/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/find /home/deploy/backups/* -maxdepth 0  -type d -ctime +40 -exec rm -rf {} \;'
echo " "

sleep 3s
echo " checking out the files...."
echo "fetch mdm-batch-app-${RELEASE}.jar from Artifactory..."
echo ""
/bin/wget --no-check-certificate https://maven.incomm.com/artifactory/incomm-release/com/incomm/mdm/mdm-batch-app/${RELEASE}/mdm-batch-app-${RELEASE}.jar

#/bin/wget -q --no-check-certificate https://maven.incomm.com/artifactory/incomm-snapshot/com/incomm/mdm/mdm-batch-app/${RELEASE}/mdm-batch-app-${RELEASE}.jar

#moved to git. delete this later
#echo "svn export the configuration files..."
#mdm-batch-app.conf
#svn export https://svn.incomm.com/svn/devel/tp/mdm/mdm-batch-app/config/versions/${CONFIG_BRANCH}/prod/ batch || exit 1


echo ""
echo "Stopping mdm-batch-app-service..."
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/sbin/service mdm-batch-app-service stop'


sleep 3s


if [ ${SKIP_BACKUP} != "Yes" ]
then
	ssh -i /app/jenkins/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} [[ -f  "'/var/opt/mdm-batch-app/mdm-batch-app.jar'" ]]
	if [ $? != "1" ]
	then
		echo " create folders and backup the jar and configs..."
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} 'mkdir -p /home/deploy/backups/mdm-batch_$(date +%Y-%m-%d)' || exit 1

		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/cp /var/opt/mdm-batch-app/mdm-batch-app.jar /home/deploy/backups/mdm-batch_$(date +%Y-%m-%d)/' || exit 1
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/cp /var/opt/mdm-batch-app/mdm-batch-app.conf /home/deploy/backups/mdm-batch_$(date +%Y-%m-%d)/' || exit 1
	else
		echo " "
		echo " mdm-batch-app does not exist. run again and select no backup or investigate the problem"
		echo " "
	fi
else
echo "Skipping Backup"
fi

echo "change permissions before deleteing files..."
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chattr -i /var/opt/mdm-batch-app/mdm-batch-app.jar' || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chattr -i /var/opt/mdm-batch-app/mdm-batch-app.conf' || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chown -R root:root /var/opt/mdm-batch-app/mdm-batch-app.jar' || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chown -R root:root /var/opt/mdm-batch-app/mdm-batch-app.conf' || exit 1

echo " delete jar and mdm-batch-app-service folder...."
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} 'whoami' || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} 'rm -f /var/opt/mdm-batch-app/mdm-batch-app.jar' || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} 'rm -rf /var/opt/mdm-batch-app/mdm-batch-app-service' || exit 1


sleep 2s

echo ""
echo "deleting configuration files..."
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} 'rm -f /var/opt/mdm-batch-app/mdm-batch-app.conf' || exit 1
sleep 3s
echo "copying configuration files...."
ls -ltr batch/

scp -i ~/.ssh/pipeline -q config/versions/${CONFIG_BRANCH}/prod/mdm-batch-app.conf root@${HOSTNAME}:/var/opt/mdm-batch-app/ || exit 1

sleep 2s
echo " "
echo "Copying mdm-batch-app-${RELEASE}.jar file..."
scp -i ~/.ssh/pipeline -q mdm-batch-app-${RELEASE}.jar root@${HOSTNAME}:/var/opt/mdm-batch-app/mdm-batch-app.jar || exit 1


sleep 2s
echo "setting ownership..."
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chown -R mdmBatchAppUser:mdmBatchAppUser /var/opt/mdm-batch-app/' || exit 1
#ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chown -R mdmBatchAppUser:mdmBatchAppUser /var/opt/mdm-batch-app/logs' || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chown -R mdmBatchAppUser:mdmBatchAppUser /var/opt/mdm-batch-app/mdm-batch-app.conf' || exit 1

echo "setting permissions..."
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chmod -R 500  /var/opt/mdm-batch-app/mdm-batch-app.jar' || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chmod -R 400  /var/opt/mdm-batch-app/mdm-batch-app.conf' || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chattr +i /var/opt/mdm-batch-app/mdm-batch-app.jar' || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chattr +i /var/opt/mdm-batch-app/mdm-batch-app.conf' || exit 1


echo "Starting the service...."
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/sbin/service mdm-batch-app-service start'
