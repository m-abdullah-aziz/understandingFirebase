/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous"; //was used earlier for sending messages without authetication, now used when user logs-out
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;    //limit of msg size

    private ListView mMessageListView;  //list view for displaying
    private MessageAdapter mMessageAdapter; //adapter for adding messages
    private ProgressBar mProgressBar;   //ignore
    private ImageButton mPhotoPickerButton; //photo selecter button
    private EditText mMessageEditText;  // edit text to get written texts
    private Button mSendButton;

    private String mUsername;   //we will send username through this to friendly chat msg object
    /*these are discussed ahead where they are initialized*/
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessagesDatabaseReference;
    private ChildEventListener mChildEventListener;
    private FirebaseAuth mFireBaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotosStorageReference;
    public static final int RC_SIGN_IN = 1; /* constant used for telling that the activity that returned a result
    was sign up or log in activity*/
    private static final int RC_PHOTO_PICKER =  2;  /*constant used to telling that the activity that
    returned the result was for selecting pics*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(getApplicationContext()); //initialize firebase for getting it's object's references in the app

        mUsername = ANONYMOUS;  //initially username is anonymous until signup or log in

        mFirebaseDatabase = FirebaseDatabase.getInstance(); /*getting a singleton
            instance of the database object , used to get root access of tree, will be used to get
            reference of objects/tables within it.
            Consider this the database.
        */
        mFireBaseAuth = FirebaseAuth.getInstance(); /*used to get an instance of authorization
            for sign up , login, checking which user is signed up, logged in
        */
        mFirebaseStorage = FirebaseStorage.getInstance();   /*will get the instance of the
        main folder of storage server containing the sub folders which
        will have our pictures and documents stored on server side*/

        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        /*READ CAREFULLY: this is important, this will use the database object to create a
        table/object within it. that is it will create a table named messsages that can store our messages
        */
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");
        /*READ CAREFULLY: this is important, this will use the storage object to create a
        subfolder within it. that is it will create a folder name chat_photos that cn store our pictures
        ,use seperate for users data , messages and products.
        */


        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();

        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            //textwatcher ensures that no empty msgs are sent.
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            //implemented as it is must
            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        //following is used to set the limit of text msg size:
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(),mUsername,null);
                mMessagesDatabaseReference.push().setValue(friendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });
        /*on start activity for login and sign up(not an use for u just see the
        firebaseAuth.getCurrentUser(), you would have to initialize the listener
        in the same manner*/
        /*Following checks if user is sign up / logged in, if not it listens to user sign up/log in:*/
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if(user != null)
                {
                    //user is signed in
                    //Toast.makeText(getApplicationContext(),"Welcome!",Toast.LENGTH_SHORT).show();
                    onSignedInInitialize(user.getDisplayName());
                }
                else{
                    //user is signed out
                    onSignedOutCleanUp();
                    //open sign in activity:
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)   //remember password?
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.GoogleBuilder().build(),   //google sign up/login
                                            new AuthUI.IdpConfig.EmailBuilder().build()     //sign up/login via email
                                            ))
                                    .setLogo(R.drawable.getit)  //setting logo. different sizes included, OS decides according to orientation and size
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };
        /*Query is used for getting data without inserting it*/
       // Query query = mFirebaseDatabase.getReference("messages").orderByChild("name").equalTo(" Uzair Rehman ");
        //query.addListenerForSingleValueEvent(valueEventListener);
    }
    //check results by photo select activity and log in activity:
    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        //check if this result is by   login/sign up, constant defined above
        if(requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {  //user signed up
                Toast.makeText(getApplicationContext(), "Signed In!", Toast.LENGTH_SHORT).show();
//                mMessagesDatabaseReference.addListenerForSingleValueEvent(valueEventListener);
        //         Query query = mFirebaseDatabase.getReference("messages").orderByChild("name").equalTo("Muhammad Abdullah");
                /*the above query matches messages with "name" field equal to "Muhammad Abdullah"*/
                //query.addListenerForSingleValueEvent(valueEventListener);
          //      query.addValueEventListener(valueEventListener);
            } else if (resultCode == RESULT_CANCELED) { //user cancelled

                Toast.makeText(getApplicationContext(), "Sign in Cancelled", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        else if(requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK){ /* user selected a picture*/
            Uri selectedImageUri = data.getData();  /*get the picture in uri form, data is in activity
                result function declaration, see function name.
            */
            final StorageReference photoRef = mChatPhotosStorageReference.child(selectedImageUri.getLastPathSegment());/*IMPORTANT: get a reference to the place it need to store the picture,
            that is what will be the pictures location on storage server, this will just append the last part of the picture name on your
            mobile to the current path we have, that is of the chat_photos folder and next code will place it on
            server.
            */
            /*following line places the picture on server and adds a success listener on it,
            that is it will listen to it being successfully uploaded.*/
            photoRef.putFile(selectedImageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {   //will check if photo got upload to storage server
                    photoRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {//checks if we have gotten the url of the picture uploaded to the server, can be got through query too.bu we will have to know user's name or email (unique key)
                        @Override
                        public void onSuccess(Uri uri) {
                            Uri downloadUrl = uri;  //getting the download link to reference the pic.
                            FriendlyMessage friendlyMessage = new FriendlyMessage(null,mUsername,downloadUrl.toString());
                            //friendly chat message object will contain user's name with either a picture or some text from the user
                            //this is defined by us.
                            mMessagesDatabaseReference.push().setValue(friendlyMessage);
                            /*this will push the object and set the value according to
                            the object's data members.
                            */
                            //will place a new objects

                        }
                    });
                }
            });
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        /*attaches the auth listener after you close the app and return, if user has not
        signed up/logged auth state listener will take care of that*/
        mFireBaseAuth.addAuthStateListener(mAuthStateListener);
    }
    @Override
    protected void onPause(){
        super.onPause();
        //remove auth listener while closing app, if user has signed in.
        if(mAuthStateListener != null)
            mFireBaseAuth.removeAuthStateListener(mAuthStateListener);
        detachDatabaseReadListener();        //remove the database listener, which checks if any new message(child was added)
        mMessageAdapter.clear();    //remove all messages from the adapter
    }
    //following menu contains signout option:
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.sign_out_menu:
                //sign out using this:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onSignedInInitialize(String username){
        //will receive name from auth listener and attach that the msg object
        mUsername = username;
        //listen to new messages coming to data base table messages::
        attachDatabaseReadListener();

    }
    private void onSignedOutCleanUp(){
        mUsername = ANONYMOUS;  //remove user name
        mMessageAdapter.clear();    //clear adapter from msgs
        detachDatabaseReadListener();   //remove the database listener, will not listen to msgs being added.
    }
    private void detachDatabaseReadListener() {
        if (mChildEventListener != null) {  //if not already detached , remove db listener
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);    //stops checking for new entries in db
            mChildEventListener = null;
        }
    }
    /*IMPORTANT:
    * used to query db, does same work as ChildEventListener but depending on a query.
    * it will display result of a query in simple words.
    * */
    ValueEventListener valueEventListener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            if(dataSnapshot.exists()) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    FriendlyMessage friendlyMessage = snapshot.getValue(FriendlyMessage.class);

                    mMessageAdapter.add(friendlyMessage);
                }
            }
            else
                Toast.makeText(getApplicationContext(),"Wrong!",Toast.LENGTH_LONG).show();
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {
            Toast.makeText(getApplicationContext(),"Error",Toast.LENGTH_LONG).show();
        }
    };
    //start checking for msgs in db
    private void attachDatabaseReadListener(){
        if(mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                //check for new msgs added in messages table:
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(friendlyMessage);
                    //if any new msgs , display them.
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };
            //add this listener to our messages table with the db:
            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
            //ignore:
            //Query query = mFirebaseDatabase.getReference("messages").orderByChild("name").equalTo("Muhammad Abdullah");
            //query.addValueEventListener(valueEventListener);

        }
    }
}
