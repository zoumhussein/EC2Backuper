#! /bin/sh
#Zoumana TRAORE, AfricaSys 2013-2015
#Please call java -jar ec2backuper.jar -h to get help
# true = production mode (really create/delete backup on EC2; false = test mode (read only on EC2)
# 082745848416 = AWS account id
# 7 = number of days to keep the backup (we remove backup older than 7 days)
# you can specify region like this EU_WEST_1 (which is the default)
java -jar ec2backuper.jar AKIAJZTK5FWEFNZEMO6A HDf7LBIaLQmjGrjpnjtttWDkQhMaCC9Z/aZA/7da true 082745848416 7