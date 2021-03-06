package org.yaxim.androidclient.data;

import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider.RosterConstants;
import org.yaxim.androidclient.service.IXMPPChatService;
import org.yaxim.androidclient.R;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class ChatHelper {

	public static void markAllAsRead(Context ctx) {
		ContentValues cv = new ContentValues();
		cv.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT_OR_READ);
		ctx.getContentResolver().update(ChatProvider.CONTENT_URI, cv,
						ChatConstants.DIRECTION + " = " + ChatConstants.INCOMING + " AND "
						+ ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW, null);
	}
	
	public static void markAsRead(Context ctx, String jid) {
		ContentValues cv = new ContentValues();
		cv.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT_OR_READ);
		ctx.getContentResolver().update(ChatProvider.CONTENT_URI, cv,
				ChatProvider.ChatConstants.JID + " = ? AND "
						+ ChatConstants.DIRECTION + " = " + ChatConstants.INCOMING + " AND "
						+ ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW,
				new String[]{jid});
	}

	public static void clearAndRespond(Context ctx, BroadcastReceiver br, String jid, String response) {
		// mark message(s) as read
		markAsRead(ctx, jid);

		// obtain service reference if possible
		Intent serviceIntent = new Intent(ctx, org.yaxim.androidclient.service.XMPPService.class);
		serviceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");
		IXMPPChatService.Stub cs = (IXMPPChatService.Stub)br.peekService(ctx, serviceIntent);
		if (cs == null) {
			android.util.Log.d("ChatHelper", "Could not peek Service for " + jid);
			return;
		}
		try {
			cs.clearNotifications(jid);
			if (response != null && response.length() > 0)
				cs.sendMessage(jid, response);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public static void sendMessage(final Context ctx, final String jid, final String message) {
		Intent serviceIntent = new Intent(ctx, org.yaxim.androidclient.service.XMPPService.class);
		serviceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");
		ServiceConnection c = new ServiceConnection() {
			public void onServiceConnected(ComponentName name, IBinder service) {
				IXMPPChatService chatService = IXMPPChatService.Stub.asInterface(service);
				try {
					if (message != null)
						chatService.sendMessage(jid, message);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				ctx.unbindService(this);
			}
			public void onServiceDisconnected(ComponentName name) {}
		};
		ctx.bindService(serviceIntent, c, Context.BIND_AUTO_CREATE);
	}

	public static void removeChatHistory(Context ctx, String jid) {
		// TODO: MUC PM history
		ctx.getContentResolver().delete(ChatProvider.CONTENT_URI,
				ChatProvider.ChatConstants.JID + " = ?", new String[] { jid });
	}

	public static void removeChatHistoryDialog(final Context ctx, final String jid, final String userName) {
		new AlertDialog.Builder(ctx)
			.setTitle(R.string.deleteChatHistory_title)
			.setMessage(ctx.getString(R.string.deleteChatHistory_text, userName, jid))
			.setPositiveButton(android.R.string.yes,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							removeChatHistory(ctx, jid);
						}
					})
			.setNegativeButton(android.R.string.no, null)
			.create().show();
	}


	private static final String[] ROSTER_QUERY = new String[] {
		RosterConstants.JID,
		RosterConstants.ALIAS,
	};
	public static final int ROSTER_FILTER_ALL = 0;
	public static final int ROSTER_FILTER_CONTACTS = 1;
	public static final int ROSTER_FILTER_MUCS = 2;
	public static List<String[]> getRosterContacts(Context ctx, int filter) {
		// we want all, online and offline
		List<String[]> list = new ArrayList<String[]>();
		String selection = null;
		if (filter == ROSTER_FILTER_CONTACTS)
			selection = "roster_group != '" + RosterConstants.MUCS + "'";
		else if (filter == ROSTER_FILTER_MUCS)
			selection = "roster_group == '" + RosterConstants.MUCS + "'";
		Cursor cursor = ctx.getContentResolver().query(RosterProvider.CONTENT_URI, ROSTER_QUERY,
					selection, null, RosterConstants.ALIAS);
		int JIDIdx = cursor.getColumnIndex(RosterConstants.JID);
		int aliasIdx = cursor.getColumnIndex(RosterConstants.ALIAS);
		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			String jid = cursor.getString(JIDIdx);
			String alias = cursor.getString(aliasIdx);
			if ((alias == null) || (alias.length() == 0)) alias = jid;
			list.add(new String[] { jid, alias });
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

	public static Collection<String> getXMPPDomains(Context ctx, int filter) {
		HashSet<String> servers = new HashSet<String>();
		for (String[] c : getRosterContacts(ctx, filter)) {
			String[] jid_split = c[0].split("@", 2);
			if (jid_split.length > 1)
				servers.add(jid_split[1]);
		}
		return servers;
	}
}
