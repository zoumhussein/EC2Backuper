package com.africasys.aws.tools;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.CreateSnapshotRequest;
import com.amazonaws.services.ec2.model.DeleteSnapshotRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSnapshotsRequest;
import com.amazonaws.services.ec2.model.DescribeSnapshotsResult;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.Snapshot;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Volume;

/**
 * 
 * @author Zoumana TRAORE
 * <b>
 * {@linkplain <a href="http://www.africasys.com"> AfricaSys 2013-2015 </a>}
 *</b>
 * <p>
 * 	This class uses AWS API and a given Account credential to target its:
 * <li> active (in-use) Volumes </li> and make Snapshots of them
 * <li> older than BACKUP_RETENTION_DAYS snapshots owned by this AWS_ACCOUNT_ID </li> and delete them (in case the previous backup succeeded of course)
 * </p>
 * </br>
 * <b> Please change to TRUE the program argument boolean if you want to activate it in production mode which will: </b>
 * <li> Create snapshots for EC2 instances volumes</li>
 * <li> Delete older snapshots from EC2</li>
 * </br>
 * </br><i>It should be completed by an email API to do reports to Infrastructure Administrators</i>
 * 
 */
public class EC2BackupService {

    static AmazonEC2 ec2;
    static int BACKUP_RETENTION_DAYS = 7;
    static boolean PRODUCTION_MODE = false; 
    static String AWS_ACCOUNT_ID = "";
    static boolean isHelpMod = false;

    private static void init(String[] args) throws Exception {
    	
    	 Region region = Region.getRegion(Regions.EU_WEST_1); //Ireland by default
         String accessKey = null;
         String secretKey = null;

     	if(args != null && args.length > 0){
     		
     		if(args[0].contains("-h")){
	 		   System.out.println("===================================================================================================================================================================================");
	 		   System.out.println("AFRICASYS 2013-2015 EC2BackupService Usage: java -jar EC2Backuper.jar AccessKey SecretKey IS_Production_Mode AWS_Account_ID [BackupRetentionDayDuration] [AWS_Region] ");
	  		   System.out.println("===================================================================================================================================================================================");
	  		   isHelpMod = true;
      		   return;
     		}
     		if(args.length <2){
     			throw new Exception(" ProgrammArgumentNumberException; You must provide at least 2 arguments$. Please use -h option to get usage");
     		}
     		
     		
     		accessKey = args[0];
     		secretKey = args[1];
 			PRODUCTION_MODE = Boolean.valueOf(args[2]);
 			AWS_ACCOUNT_ID = args[3];
 			
 			if(args.length >=5){
 				BACKUP_RETENTION_DAYS = Integer.parseInt(args[4]);
 			}
 			
 			if(args.length >=6){
 				region = Region.getRegion(Regions.valueOf(args[5]));
 			}
     	}

        AWSCredentials credentials = null;
        try {
			credentials = new  BasicAWSCredentials(accessKey, secretKey);
        } catch (Exception e) {
          e.printStackTrace();
        }
        ec2 = new AmazonEC2Client(credentials);
 		ec2.setRegion(region);
    }


    public static void main(String[] args) throws Exception {

        init(args);
        if(isHelpMod){
        	return;
        }

        boolean isExcecutionSuccessful = true;

        System.out.println("=====================================================================================================================================");
        System.out.println("AFRICASYS 2013-2015 EC2BackupService launched at "+new Date().toString()+" in "+(PRODUCTION_MODE?"Production Mode":"Test Mode"));
        System.out.println("=====================================================================================================================================");

        try {
        	
        	//describes AZ
            DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
            
            System.out.println("You have access to Availability Zones.");
            for(AvailabilityZone zone : availabilityZonesResult.getAvailabilityZones()){
                System.out.println(zone.getZoneName()+" : "+zone.getRegionName());
            }
            
            //Find all active volumes (to be backuped as snapshots)
            HashMap<String, String> volumesToBackup = new HashMap<String, String>();
            DescribeVolumesResult describeVolumes = ec2.describeVolumes();
            System.out.println("You have "+describeVolumes.getVolumes().size()+" volumes");
            
            for(Volume volume : describeVolumes.getVolumes()){
            	if(volume.getState().equals("in-use")){

            		String linkedInstanceId = volume.getAttachments().get(0).getInstanceId();
            		DescribeInstancesRequest instanceRequest = new DescribeInstancesRequest();
            		List<String> instanceToDescribe = new ArrayList<String>();
            		instanceToDescribe.add(linkedInstanceId);
            		instanceRequest.setInstanceIds(instanceToDescribe);
                    DescribeInstancesResult describeInstancesResult = ec2.describeInstances(instanceRequest);
                    List<Tag> tags = describeInstancesResult.getReservations().get(0).getInstances().get(0).getTags();
                    boolean toBackup = false;
                    String instanceName = "";
                    for(Tag tag : tags){
                    	//find a tag with "backup" in the name
                    	if(tag.getKey().toLowerCase().contains("backup")){
                    		toBackup = true;
                    	}
                    	if(tag.getKey().equalsIgnoreCase("name")){
                    		instanceName = tag.getValue();
                    	}
                    }
                    if(toBackup){
                		volumesToBackup.put(volume.getVolumeId(), !instanceName.isEmpty()?instanceName:describeInstancesResult.getReservations().get(0).getInstances().get(0).getTags().get(0).getValue()/*as replacement to tag NAME not found*/);
                    }
            	}
            }
            System.out.println("You have "+volumesToBackup.size()+" ACTIVE volumes that will be backuped");
            
            //Realize the real snapshot for all active volume
	    	  for(String volumeId : volumesToBackup.keySet()){
	              System.out.println("Backing up the volumeId: "+volumeId+" as snapshot for instance: "+volumesToBackup.get(volumeId));
	          	CreateSnapshotRequest request = new CreateSnapshotRequest(volumeId, volumesToBackup.get(volumeId));
	          	request.setDescription("snapshot of date: "+getFormatedDate()+" for instance: "+volumesToBackup.get(volumeId));
	            if(PRODUCTION_MODE){
	            	ec2.createSnapshot(request);
	            }
	          }
          
            
            //for debug purpose list all available snapshots to the AWS account
            if(!PRODUCTION_MODE){
            	DescribeSnapshotsResult results = ec2.describeSnapshots();
                System.out.println("You have "+results.getSnapshots().size()+" snapshots");
                for(Snapshot snapshot : results.getSnapshots()){
            		System.out.println("Snapshot "+snapshot.getSnapshotId()+ " with owners "+snapshot.getOwnerId());
            		if(snapshot.getOwnerId().equals(AWS_ACCOUNT_ID)){
            			System.out.println("Yes this belong to us!");
            		}
            	}
            }
            
            //Clean old snapshots
        	 DescribeSnapshotsRequest request = new DescribeSnapshotsRequest();
             List<String> ownerIds = new ArrayList<String>();
             ownerIds.add(AWS_ACCOUNT_ID);
             request.setOwnerIds(ownerIds);
             DescribeSnapshotsResult result = ec2.describeSnapshots(request);
             System.out.println("You have "+result.getSnapshots().size()+" snapshots");
             for(Snapshot current : result.getSnapshots()){
                 int diffDays = (int) ((new Date().getTime() - current.getStartTime().getTime()) / (24 * 60 * 60 * 1000));
                 if(!PRODUCTION_MODE){
                	 System.out.println("Snapshot "+current.getDescription()+ " was made "+diffDays+" ago!");
                 }
                 
                 //avoiding to delete other snapshots not created by the tools
                 boolean snapshotCreatedByUs = current.getDescription().contains("snapshot of date:") || current.getDescription().contains("ec2backuper") ;
                 
                 if(BACKUP_RETENTION_DAYS < diffDays && snapshotCreatedByUs){
                 	System.out.println("Deleting an old snapshot older for "+diffDays+" : "+current.getSnapshotId()+" initiated on: "+current.getStartTime());
                 	DeleteSnapshotRequest deleteSnapshotRequest = new DeleteSnapshotRequest(current.getSnapshotId());
    	            if(PRODUCTION_MODE){
    	            	ec2.deleteSnapshot(deleteSnapshotRequest);
    	            }
                 }
             }
            
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
                isExcecutionSuccessful = false;
        }
        
        
        System.out.println("===================================================================================");
        System.out.println("EC2BackupService finished at "+new Date().toString()+" with "+(isExcecutionSuccessful?" *** SUCCESS ***":"!!! ERROR !!!"));
        System.out.println("===================================================================================");
        
    }//main
    
    private static String getFormatedDate(){
        SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm:SS");
        return DATE_FORMAT.format(new Date());
    }

}
