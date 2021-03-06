/*
 * Copyright (c) 2013, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.apptentive.android.sdk.Log;
import com.apptentive.android.sdk.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * There can be only one. SQLiteOpenHelper per database name that is. All new Apptentive tables must be defined here.
 *
 * @author Sky Kelsey
 */
public class ApptentiveDatabase extends SQLiteOpenHelper implements PayloadStore, EventStore, MessageStore, FileStore {

	// COMMON
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "apptentive";
	private static final int TRUE = 1;
	private static final int FALSE = 0;

	// PAYLOAD
	private static final String TABLE_PAYLOAD = "payload";
	private static final String PAYLOAD_KEY_DB_ID = "_id";           // 0
	private static final String PAYLOAD_KEY_BASE_TYPE = "base_type"; // 1
	private static final String PAYLOAD_KEY_JSON = "json";           // 2

	private static final String TABLE_CREATE_PAYLOAD =
			"CREATE TABLE " + TABLE_PAYLOAD +
					" (" +
					PAYLOAD_KEY_DB_ID + " INTEGER PRIMARY KEY, " +
					PAYLOAD_KEY_BASE_TYPE + " TEXT, " +
					PAYLOAD_KEY_JSON + " TEXT" +
					");";

	private static final String QUERY_PAYLOAD_GET_NEXT_TO_SEND = "SELECT * FROM " + TABLE_PAYLOAD + " ORDER BY " + PAYLOAD_KEY_DB_ID + " ASC LIMIT 1";


	// MESSAGE
	private static final String TABLE_MESSAGE = "message";
	private static final String MESSAGE_KEY_DB_ID = "_id";                           // 0
	private static final String MESSAGE_KEY_ID = "id";                               // 1
	private static final String MESSAGE_KEY_CLIENT_CREATED_AT = "client_created_at"; // 2
	private static final String MESSAGE_KEY_NONCE = "nonce";                         // 3
	private static final String MESSAGE_KEY_STATE = "state";                         // 4
	private static final String MESSAGE_KEY_READ = "read";                           // 5
	private static final String MESSAGE_KEY_JSON = "json";                           // 6

	private static final String TABLE_CREATE_MESSAGE =
			"CREATE TABLE " + TABLE_MESSAGE +
					" (" +
					MESSAGE_KEY_DB_ID + " INTEGER PRIMARY KEY, " +
					MESSAGE_KEY_ID + " TEXT, " +
					MESSAGE_KEY_CLIENT_CREATED_AT + " DOUBLE, " +
					MESSAGE_KEY_NONCE + " TEXT, " +
					MESSAGE_KEY_STATE + " TEXT, " +
					MESSAGE_KEY_READ + " INTEGER, " +
					MESSAGE_KEY_JSON + " TEXT" +
					");";

	private static final String QUERY_MESSAGE_GET_BY_NONCE = "SELECT * FROM " + TABLE_MESSAGE + " WHERE " + MESSAGE_KEY_NONCE + " = ?";
	// Coalesce returns the second arg if the first is null. This forces the entries with null IDs to be ordered last in the list until they do have IDs because they were sent and retrieved from the server.
	private static final String QUERY_MESSAGE_GET_ALL_IN_ORDER = "SELECT * FROM " + TABLE_MESSAGE + " ORDER BY COALESCE(" + MESSAGE_KEY_ID + ", 'z') ASC";
	private static final String QUERY_MESSAGE_GET_LAST_ID = "SELECT " + MESSAGE_KEY_ID + " FROM " + TABLE_MESSAGE + " WHERE " + MESSAGE_KEY_STATE + " = '" + Message.State.saved + "' AND " + MESSAGE_KEY_ID + " NOTNULL ORDER BY " + MESSAGE_KEY_ID + " DESC LIMIT 1";
	private static final String QUERY_MESSAGE_UNREAD = "SELECT " + MESSAGE_KEY_ID + " FROM " + TABLE_MESSAGE + " WHERE " + MESSAGE_KEY_READ + " = " + FALSE + " AND " + MESSAGE_KEY_ID + " NOTNULL";


	// FileStore
	private static final String TABLE_FILESTORE = "file_store";
	private static final String FILESTORE_KEY_ID = "id";                         // 0
	private static final String FILESTORE_KEY_MIME_TYPE = "mime_type";           // 1
	private static final String FILESTORE_KEY_ORIGINAL_URL = "original_uri";     // 2
	private static final String FILESTORE_KEY_LOCAL_URL = "local_uri";           // 3
	private static final String FILESTORE_KEY_APPTENTIVE_URL = "apptentive_uri"; // 4
	private static final String TABLE_CREATE_FILESTORE =
			"CREATE TABLE " + TABLE_FILESTORE +
					" (" +
					FILESTORE_KEY_ID + " TEXT PRIMARY KEY, " +
					FILESTORE_KEY_MIME_TYPE + " TEXT, " +
					FILESTORE_KEY_ORIGINAL_URL + " TEXT, " +
					FILESTORE_KEY_LOCAL_URL + " TEXT, " +
					FILESTORE_KEY_APPTENTIVE_URL + " TEXT" +
					");";


	public ApptentiveDatabase(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	/**
	 * This function is called only for new installs, and onUpgrade is not called in that case. Therefore, you must include the
	 * latest complete set of DDL here.
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.d("ApptentiveDatabase.onCreate(db)");
		db.execSQL(TABLE_CREATE_PAYLOAD);
		db.execSQL(TABLE_CREATE_MESSAGE);
		db.execSQL(TABLE_CREATE_FILESTORE);

	}

	/**
	 * This method is called when an app is upgraded. Add alter table statements here for each version in a non-breaking
	 * switch, so that all the necessary upgrades occur for each older version.
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d("ApptentiveDatabase.onUpgrade(db, %d, %d)", oldVersion, newVersion);
		switch (oldVersion) {
			case 1:
			case 2:
		}
	}

	// PAYLOAD: This table is used to store all the Payloads we want to send to the server.

	/**
	 * If an item with the same nonce as an item passed in already exists, it is overwritten by the item. Otherwise
	 * a new message is added.
	 */
	public synchronized void addPayload(Payload... payloads) {
		SQLiteDatabase db = this.getWritableDatabase();

		db.beginTransaction();
		for (Payload payload : payloads) {
			ContentValues values = new ContentValues();
			values.put(PAYLOAD_KEY_BASE_TYPE, payload.getBaseType().name());
			values.put(PAYLOAD_KEY_JSON, payload.toString());
			db.insert(TABLE_PAYLOAD, null, values);
		}
		db.setTransactionSuccessful();
		db.endTransaction();
		db.close();
	}

	public synchronized void deletePayload(Payload payload) {
		if (payload != null) {
			SQLiteDatabase db = getWritableDatabase();
			db.delete(TABLE_PAYLOAD, PAYLOAD_KEY_DB_ID + " = ?", new String[]{Long.toString(payload.getDatabaseId())});
			db.close();
		}
	}

	public synchronized void deleteAllPayloads() {
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_PAYLOAD, "", null);
		db.close();
	}

	public synchronized Payload getOldestUnsentPayload() {
		SQLiteDatabase db = getReadableDatabase();
		Cursor cursor = db.rawQuery(QUERY_PAYLOAD_GET_NEXT_TO_SEND, null);
		Payload payload = null;
		if (cursor.moveToFirst()) {
			long databaseId = Long.parseLong(cursor.getString(0));
			Payload.BaseType baseType = Payload.BaseType.parse(cursor.getString(1));
			String json = cursor.getString(2);
			payload = PayloadFactory.fromJson(json, baseType);
			payload.setDatabaseId(databaseId);
		}
		cursor.close();
		db.close();
		return payload;
	}


	// MessageStore

	/**
	 * Saves the message into the message table, and also into the payload table so it can be sent to the server.
	 */
	public synchronized void addOrUpdateMessages(Message... messages) {
		SQLiteDatabase db = this.getWritableDatabase();
		try {
			for (Message message : messages) {
				Cursor cursor = db.rawQuery(QUERY_MESSAGE_GET_BY_NONCE, new String[]{message.getNonce()});
				if (cursor.moveToFirst()) {
					// Update
					String databaseId = cursor.getString(0);
					ContentValues messageValues = new ContentValues();
					messageValues.put(MESSAGE_KEY_ID, message.getId());
					messageValues.put(MESSAGE_KEY_STATE, message.getState().name());
					if(message.isRead()) { // A message can't be unread after being read.
						messageValues.put(MESSAGE_KEY_READ, TRUE);
					}
					messageValues.put(MESSAGE_KEY_JSON, message.toString());
					db.update(TABLE_MESSAGE, messageValues, MESSAGE_KEY_DB_ID + " = ?", new String[]{databaseId});
				} else {
					// Insert
					db.beginTransaction();
					ContentValues messageValues = new ContentValues();
					messageValues.put(MESSAGE_KEY_ID, message.getId());
					messageValues.put(MESSAGE_KEY_CLIENT_CREATED_AT, message.getClientCreatedAt());
					messageValues.put(MESSAGE_KEY_NONCE, message.getNonce());
					messageValues.put(MESSAGE_KEY_STATE, message.getState().name());
					messageValues.put(MESSAGE_KEY_READ, message.isRead() ? TRUE : FALSE);
					messageValues.put(MESSAGE_KEY_JSON, message.toString());
					db.insert(TABLE_MESSAGE, null, messageValues);
					db.setTransactionSuccessful();
					db.endTransaction();
				}
				cursor.close();
			}
		} finally {
			db.close();
		}
	}

	public synchronized void updateMessage(Message message) {
		SQLiteDatabase db = this.getWritableDatabase();
		try {
			db.beginTransaction();
			ContentValues values = new ContentValues();
			values.put(MESSAGE_KEY_ID, message.getId());
			values.put(MESSAGE_KEY_CLIENT_CREATED_AT, message.getClientCreatedAt());
			values.put(MESSAGE_KEY_NONCE, message.getNonce());
			values.put(MESSAGE_KEY_STATE, message.getState().name());
			if(message.isRead()) { // A message can't be unread after being read.
				values.put(MESSAGE_KEY_READ, TRUE);
			}
			values.put(MESSAGE_KEY_JSON, message.toString());
			db.update(TABLE_MESSAGE, values, MESSAGE_KEY_NONCE + " = ?", new String[]{message.getNonce()});
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
			db.close();
		}
	}

	public synchronized List<Message> getAllMessages() {
		List<Message> messages = new ArrayList<Message>();
		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(QUERY_MESSAGE_GET_ALL_IN_ORDER, null);

		if (cursor.moveToFirst()) {
			do {
				String json = cursor.getString(6);
				Message message = MessageFactory.fromJson(json);
				if (message == null) {
					Log.e("Error parsing Record json from database: %s", json);
					continue;
				}
				message.setDatabaseId(cursor.getLong(0));
				message.setState(Message.State.parse(cursor.getString(4)));
				message.setRead(cursor.getInt(5) == TRUE);
				messages.add(message);
			} while (cursor.moveToNext());
		}
		cursor.close();
		db.close();
		return messages;
	}

	public synchronized String getLastReceivedMessageId() {
		SQLiteDatabase db = getReadableDatabase();
		Cursor cursor = db.rawQuery(QUERY_MESSAGE_GET_LAST_ID, null);
		String ret = null;
		if (cursor.moveToFirst()) {
			ret = cursor.getString(0);
		}
		cursor.close();
		db.close();
		return ret;
	}

	public synchronized int getUnreadMessageCount() {
		SQLiteDatabase db = getReadableDatabase();
		Cursor cursor = db.rawQuery(QUERY_MESSAGE_UNREAD, null);
		int ret = cursor.getCount();
		cursor.close();
		db.close();
		return ret;
	}

	//
	// File Store
	//

	public synchronized StoredFile getStoredFile(String id) {
		StoredFile ret = null;
		SQLiteDatabase db = getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_FILESTORE + " WHERE " + FILESTORE_KEY_ID + " = ?", new String[]{id});
		if (cursor.moveToFirst()) {
			ret = new StoredFile();
			ret.setId(id);
			ret.setMimeType(cursor.getString(1));
			ret.setOriginalUri(cursor.getString(2));
			ret.setLocalFilePath(cursor.getString(3));
			ret.setApptentiveUri(cursor.getString(4));
		}
		cursor.close();
		db.close();
		return ret;
	}

	public synchronized boolean putStoredFile(StoredFile storedFile) {
		long ret;
		SQLiteDatabase db = getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(FILESTORE_KEY_ID, storedFile.getId());
		values.put(FILESTORE_KEY_MIME_TYPE, storedFile.getMimeType());
		values.put(FILESTORE_KEY_ORIGINAL_URL, storedFile.getOriginalUri());
		values.put(FILESTORE_KEY_LOCAL_URL, storedFile.getLocalFilePath());
		values.put(FILESTORE_KEY_APPTENTIVE_URL, storedFile.getApptentiveUri());

		Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_FILESTORE + " WHERE " + FILESTORE_KEY_ID + " = ?", new String[]{storedFile.getId()});
		boolean doUpdate = cursor.moveToFirst();
		cursor.close();
		if (doUpdate) {
			ret = db.update(TABLE_FILESTORE, values, FILESTORE_KEY_ID + " = ?", new String[]{storedFile.getId()});
		} else {
			ret = db.insert(TABLE_FILESTORE, null, values);
		}
		cursor.close();
		db.close();
		return ret != -1;
	}

	private void upgrade1to2() {
	}
}
