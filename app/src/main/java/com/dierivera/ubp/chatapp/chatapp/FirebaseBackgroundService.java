package com.dierivera.ubp.chatapp.chatapp;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.WearableExtender;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;
import android.util.Log;

import com.dierivera.ubp.chatapp.chatapp.models.ChatMessage;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.util.concurrent.ExecutionException;

/**
 * Created by dierivera on 8/1/17.
 */

public class FirebaseBackgroundService extends IntentService {

    private static final String TAG = "Firebase_MSG";

    public static final String EXTRA_VOICE_REPLY = "extra_voice_reply";

    private static final String imageURL = "https://firebasestorage.googleapis.com/v0/b/chat-app-112b9.appspot.com/o/cover.png?alt=media&token=0c17fadc-4e8d-47b0-9d8d-c8016ce89ecd";

    private ValueEventListener handler;
    private boolean isStarting;

    Bitmap notificationBackground = null;

    final FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference ref = database.getReference();

    public FirebaseBackgroundService() {
        super("FirebaseBackgroundService");
        // must override constructor
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.i(TAG, "service started");
        isStarting = true;
        handler = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot arg0) { //is called after onChildAdded()
                isStarting = false;
            }
            @Override
            public void onCancelled(DatabaseError databaseError) { }
        };
        ref.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
                if (!isStarting) {
                    ChatMessage newMessage = dataSnapshot.getValue(ChatMessage.class);
                    String user = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("user", "");
                    if (!user.equals(newMessage.getMessageUser())) { //checks the user
                        Log.i(TAG, newMessage.getMessageUser() + ": " + newMessage.getMessageText());
                        createWearNotification(newMessage.getMessageUser(), newMessage.getMessageText());
                    }
                }
            }
            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });

        ref.addValueEventListener(handler);
    }



    public class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        private Bitmap image;

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                image = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                image = null;
            }
            return image;
        }

        protected void onPostExecute(Bitmap result) {
        }
    }



    private void createWearNotification(String messageTitle, String messageBody){

        String replyLabel = getResources().getString(R.string.app_name);

        //array in strings.xml with the responses loaded
        String[] replyChoices = getResources().getStringArray(R.array.reply_choices);


        RemoteInput remoteInput = new RemoteInput.Builder(EXTRA_VOICE_REPLY)
                .setLabel(replyLabel)
                .setChoices(replyChoices)
                .build();

        Intent actionIntent = new Intent(this, MainActivity.class);
        PendingIntent actionPendingIntent =
                PendingIntent.getActivity(this, 0, actionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        // Create the action
        NotificationCompat.Action action =
                new NotificationCompat.Action.Builder(R.drawable.ic_send,
                        "Responder", actionPendingIntent)
                        .addRemoteInput(remoteInput)
                        .build();

        Uri defaultSoundUri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        try {
            notificationBackground = new DownloadImageTask().execute(imageURL).get();
            Log.i(TAG, "image downloaded");
        } catch (InterruptedException e) {
            notificationBackground = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.ic_send);
        } catch (ExecutionException e) {
            notificationBackground = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.ic_send);
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(messageTitle)
                .setContentText(messageBody)
                .setSound(defaultSoundUri)
                .setVibrate(new long[] {0, 100, 1000})// Start without a delay, Vibrate for 100 milliseconds, Sleep for 1000 milliseconds
                .extend(new WearableExtender().addAction(action).setBackground(notificationBackground));


        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        notificationManager.notify(0, notificationBuilder.build());
    }

}
