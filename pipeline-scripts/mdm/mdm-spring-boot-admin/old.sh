#!/bin/sh

deploymentLocation="/var/opt/spring-boot-admin"
artifactId="spring-boot-admin"
artExtension="jar"
serviceName="spring-boot-admin-service"
userId="springBootAdminUser"
groupId="springBootAdminUser"
env="prod"


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

echo " checking out the files...."
echo "fetch mdm-batch-app-${RELEASE}.jar from Artifactory..."
echo ""
/bin/wget --no-check-certificate https://maven.incomm.com/artifactory/incomm-release/com/incomm/${artifactId}/${RELEASE}/${artifactId}-${RELEASE}.${artExtension}


echo ""
echo "Stopping ${artifactId} service..."
echo "p${serviceName}p service ${serviceName} stop"
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "service ${serviceName} stop"



if [ ${SKIP_BACKUP} != "Yes" ]
then
	ssh -i /app/jenkins/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} [[ -f  "'${deploymentLocation}/${artifactId}.${artExtension}'" ]]
	if [ $? != "1" ]
	then
		echo " create folders and backup the jar and configs..."
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "mkdir /home/deploy/backups/spring-boot_$(date +%Y-%m-%d)" || exit 1

		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/cp ${deploymentLocation}/${artifactId}.${artExtension} /home/deploy/backups/spring-boot_$(date +%Y-%m-%d)/" || exit 1
		ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/cp ${deploymentLocation}/${artifactId}.conf /home/deploy/backups/spring-boot_$(date +%Y-%m-%d)/" || exit 1
	else
		echo " "
		echo " mdm-batch-app does not exist. run again and select no backup or investigate the problem"
		echo " "
	fi
else
echo "Skipping Backup"
fi

echo "deleting files..."
echo "${deploymentLocation}/${artifactId}.conf"
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/chattr -i ${deploymentLocation}/${artifactId}.${artExtension}" || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/chattr -i ${deploymentLocation}/${artifactId}.conf" || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/chown -R root:root ${deploymentLocation}/${artifactId}.${artExtension}" || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/chown -R root:root ${deploymentLocation}/${artifactId}.conf" || exit 1

ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} 'whoami' || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "rm -f ${deploymentLocation}/${artifactId}.${artExtension}" || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "rm -rf ${deploymentLocation}/${artifactId}-service" || exit 1


sleep 3s

echo ""
echo "deleting configuration files..."
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "rm -f ${deploymentLocation}/${artifactId}.conf" || exit 1
sleep 3s
echo "copying configuration files...."
#ls -ltr batch/

scp -i ~/.ssh/pipeline -q config/versions/${CONFIG_BRANCH}/prod/spring-boot-admin.conf root@${HOSTNAME}:${deploymentLocation}/ || exit 1




sleep 3s
echo " "
echo "Copying mdm-batch-app-${RELEASE}.jar file..."
scp -i ~/.ssh/pipeline -q ${artifactId}-${RELEASE}.jar root@${HOSTNAME}:${deploymentLocation}/${artifactId}.${artExtension} || exit 1


echo "setting ownership..."
echo "jar"
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/chown -R ${userId}:${groupId} ${deploymentLocation}/" || exit 1
#ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} '/bin/chown -R ${userId}:${groupId} ${deploymentLocation}/logs' || exit 1
echo "conf"
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/chown -R ${userId}:${groupId} ${deploymentLocation}/${artifactId}.conf" || exit 1
sleep 3s
echo "setting permissions..."
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/chmod -R 500  ${deploymentLocation}/${artifactId}.${artExtension}" || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/chmod -R 400  ${deploymentLocation}/${artifactId}.conf" || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/chattr +i ${deploymentLocation}/${artifactId}.${artExtension}" || exit 1
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "/bin/chattr +i ${deploymentLocation}/${artifactId}.conf" || exit 1


echo "Starting the service...."
ssh -i ~/.ssh/pipeline -q -o StrictHostKeyChecking=no root@${HOSTNAME} "service ${serviceName} start" || exit 1
