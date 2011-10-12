/*
 * PayloadUploader.java
 *
 * Created by Sky Kelsey on 2011-10-06.
 * Copyright 2011 Apptentive, Inc. All rights reserved.
 */

package com.apptentive.android.sdk.offline;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import com.apptentive.android.sdk.ALog;
import com.apptentive.android.sdk.comm.ApptentiveClient;
import com.apptentive.android.sdk.model.ApptentiveModel;

import java.util.UUID;

public class PayloadManager{

	private static final String PAYLOAD_INDEX_NAME = "apptentive-payloads-json";

	private Activity activity;

	public PayloadManager(Activity activity){
		this.activity = activity;
	}




	/////

	public void save(JSONPayload payload){
		SharedPreferences prefs = activity.getSharedPreferences("APPTENTIVE", Context.MODE_PRIVATE);
		String uuid = UUID.randomUUID().toString();
		storePayload(prefs, uuid, payload);
		addToPayloadList(prefs, uuid);
	}

	private void storePayload(SharedPreferences prefs, String name, JSONPayload payload){
		prefs.edit().putString(name, payload.getAsJSON()).commit();
	}

	private void addToPayloadList(SharedPreferences prefs, String name){
		String payloadNames = prefs.getString(PAYLOAD_INDEX_NAME, "");
		payloadNames = (payloadNames.length() == 0 ? name : payloadNames + ";" + name);
		prefs.edit().putString(PAYLOAD_INDEX_NAME, payloadNames).commit();
	}

	public String getFirstPayloadInPayloadList(){
		SharedPreferences prefs = activity.getSharedPreferences("APPTENTIVE", Context.MODE_PRIVATE);
		String[] payloadNames = prefs.getString(PAYLOAD_INDEX_NAME, "").split(";");
		if(payloadNames.length > 0){
			return prefs.getString(payloadNames[0], "");
		}
		return null;
	}

	private void deletePayload(SharedPreferences prefs, String name){
		prefs.edit().remove(name).commit();
	}

	public void deleteFirstPayloadInPayloadList(){
		SharedPreferences prefs = activity.getSharedPreferences("APPTENTIVE", Context.MODE_PRIVATE);
		String[] payloadNames = prefs.getString(PAYLOAD_INDEX_NAME, "").split(";");
		String newPayloadList = "";
		for (int i = 0; i < payloadNames.length; i++) {
			String payloadName = payloadNames[i];
			if(i == 0){
				deletePayload(prefs, payloadName);
			}else{
				newPayloadList = (newPayloadList.equals("") ? payloadName : newPayloadList + ";" + payloadName);
			}
		}
		prefs.edit().putString(PAYLOAD_INDEX_NAME, newPayloadList).commit();
	}
	//////






	public void save(Payload payload){
		SharedPreferences prefs = activity.getSharedPreferences("APPTENTIVE", Context.MODE_PRIVATE);
		Payload.store(prefs, payload);
	}

	public void run(){
		SharedPreferences prefs = activity.getSharedPreferences("APPTENTIVE", Context.MODE_PRIVATE);
		PayloadUploader uploader = new PayloadUploader(prefs);
		uploader.start();
	}


	private class PayloadUploader extends Thread{
		private ALog log = new ALog(PayloadUploader.class);
		private SharedPreferences prefs;

		public PayloadUploader(SharedPreferences prefs){
			log.e("Uploading payloads...");
			this.prefs = prefs;
		}

		@Override
		public void run() {

			PayloadManager payloadManager = new PayloadManager(activity);
			String json;
			json  = payloadManager.getFirstPayloadInPayloadList();
			while(json != null && !json.equals("")){
				log.e("JSON: " + json);
				ApptentiveClient client = new ApptentiveClient(ApptentiveModel.getInstance().getApiKey());
				boolean success = client.postJSON(json);
/* FOR TESTING
				boolean success = true;
*/
				log.e("Payload upload " + (success ? "succeeded!" : "failed!"));
				if(success){
					payloadManager.deleteFirstPayloadInPayloadList();
				}else{
					break;
				}
				json = payloadManager.getFirstPayloadInPayloadList();
			}

			Payload payload;
			while((payload  = Payload.retrieveOldest(prefs)) != null){
				log.e("Initiating payload upload for: " + payload.payloadName);
				ApptentiveClient client = new ApptentiveClient(ApptentiveModel.getInstance().getApiKey());
				boolean success = client.postFeedback(payload.getParams());
				log.e("Payload upload " + (success ? "succeeded!" : "failed!"));
				if(success){
					payload.delete(prefs);
				}else{
					break;
				}
			}
		}
	}
}