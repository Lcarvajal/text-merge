package hophacks.omarkadry.textingmerge;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.ContactsContract;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.MultiAutoCompleteTextView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;

/**
 * Created by Omar Kadry on 2/6/2015.
 * Main Activity for M-txt. This allows the users to select a group from their phone contact and
 * send a text message to each member as individual threads. It also allows them to user '@name',
 * '@first_name' and '@last_name' to personalize each message with the respective name.
 */
public class TextMerge extends Activity implements LoaderManager.LoaderCallbacks<Cursor>{

    public final String DEBUG = "!!!DEBUG!!!";
    private SimpleCursorAdapter mAdapter;
    private static final String[] FIELDS = new String[]
            //Strings of suggested text
            {
                    "@name", "@first_name", "@last_name"
            };
    private MultiAutoCompleteTextView textComplete;
    private Typeface doris_font;
    private TextView messageLen;
    private Spinner groupSpinner;
    private ImageButton sendButton;
    private TextView groupSelect;
    private TextView enterMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        doris_font = Typeface.createFromAsset(getAssets(), "dosis-semibold.ttf");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_merge);

        //Set the Static Prompts to the doris font
        groupSelect = (TextView) findViewById(R.id.select_group_prompt);
        enterMessage = (TextView) findViewById(R.id.enter_message_prompt);
        messageLen = (TextView) findViewById(R.id.message_length);
        groupSelect.setTypeface(doris_font);
        enterMessage.setTypeface(doris_font);
        messageLen.setTypeface(doris_font);

        //Loads the Loader, sets the Adapter and loads it into the group spinner
        setUpGroupSpinner();

        //Set up Auto Complete text field
        setUpMessageComposition();

        //Set up Send button
        sendButton = (ImageButton)findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View arg0)
            {
                sendTextAndExit();
            }
        });
    }

    //Initializes the GroupListLoader, loads it into the adapter for the spinner. Also goes through
    //The groups and changes the font to Doris in addition to changing Account names that refer
    //to the phone to "Saved on Phone" for clarity
    private void setUpGroupSpinner() {
        //Initialize the GroupListLoader
        getLoaderManager().initLoader(0, null, this);

        //Set the Adapter for the Groups and send it to the group spinner
        mAdapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_2,
                null,
                new String[]{ContactsContract.Groups.TITLE, ContactsContract.Groups.ACCOUNT_NAME,
                        ContactsContract.Groups.ACCOUNT_TYPE},
                new int[]{android.R.id.text1, android.R.id.text2},
                0);

        //If the Account_type is not a google account it is stored on the phone, so
        //We will change the ACCOUNT_NAME to say "Saved on Phone"
        //Account_name is not compared because I do not know if it varies between phones.
        //It is certain that the only non-google account type is the phone however, so it is used
        //to compare. Also sets all fonts to the doris font
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View aView, Cursor aCursor, int aColumnIndex) {
                TextView spinnerText;

                //Change the Font of the titles to doris font
                if(aColumnIndex == GroupListLoader.TITLE){
                    spinnerText = (TextView) aView;
                    spinnerText.setTypeface(doris_font);
                    spinnerText.setTextColor(Color.WHITE);
                }

                if (aColumnIndex == GroupListLoader.ACCOUNT_NAME) {

                    String acc_name = aCursor.getString(GroupListLoader.ACCOUNT_NAME);
                    String acc_type = aCursor.getString(GroupListLoader.ACCOUNT_TYPE);

                    //Not a Google Account
                    if(!acc_type.equals("com.google")){
                        //Change to "Saved on Phone"
                        spinnerText = (TextView) aView;
                        spinnerText.setText("Saved on Phone");
                        spinnerText.setTypeface(doris_font);
                        spinnerText.setTextColor(Color.WHITE);
                    }
                    //Is a Google Account so display the E-mail
                    else{
                        spinnerText = (TextView) aView;
                        spinnerText.setText(acc_name);
                        spinnerText.setTypeface(doris_font);
                        spinnerText.setTextColor(Color.WHITE);
                    }
                    return true;
                }
                return false;
            }
        });

        //Set the Spinner Adapter
        groupSpinner = (Spinner) findViewById(R.id.phoneGroups);
        groupSpinner.setAdapter(mAdapter);
    }

    //Sets up edit text field up with auto-complete up.
    //Also listens for changes to it to update the message_length TextView
    private void setUpMessageComposition() {
        ArrayAdapter<String> aaStr;

        textComplete = (MultiAutoCompleteTextView) this.findViewById(R.id.text_message);
        textComplete.setTypeface(doris_font);
        aaStr = new DorisArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line,
                FIELDS, doris_font);
        textComplete.setAdapter(aaStr);
        textComplete.setTokenizer(new SpaceTokenizer());

        //Set up a text watcher to update the length count
        textComplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                messageLen.setText("Message Length: "+s.length() +" Characters");
            }
        });
    }

    //Called when the send button is pressed
    //Queries for the selected groups contacts and replaces @name, @first_name, @last_name if
    //necessary.
    //If the contact group is empty a popup box will appear. If the text message is empty a popup
    //box will appear. Upon success exits the Application after a success popup box appears.
    private void sendTextAndExit() {
        ArrayList<Contact> contactList;
        String message = textComplete.getText().toString();
        String messageToSend;
        Contact currentContact;
        mtxtAlert alert;

        //If the message is blank display an alert and break
        if(message.length() == 0){
            alert=new mtxtAlert(this, "You cannot send an empty text message!");
            alert.show();
            return;
        }

        contactList = getContacts(getGroupID());

        //If the group is empty display an alert and break
        if(contactList == null){
            alert=new mtxtAlert(this, "This group has no Contacts!");
            alert.show();
            return;
        }

        //Go through all the contacts and send the text
        for(int i = 0; i < contactList.size(); i++){
            currentContact = contactList.get(i);
            messageToSend = message.replace("@first_name", currentContact.getFirstName());
            messageToSend = messageToSend.replace("@name", currentContact.getFullName());
            messageToSend = messageToSend.replace("@last_name", currentContact.getLastName());

            //Send the text to the Current Contact
            SmsManager smsText = SmsManager.getDefault();
            smsText.sendTextMessage(currentContact.getPhoneNumber(), null,
                    messageToSend, null, null);
            Log.i(DEBUG, "Text Message: '" + messageToSend + "' Sent to " + currentContact.getFullName());
        }

        //Success Sending all of the contacts, give a success dialog and exit the program
        alert=new mtxtAlert(this, "Text Message Sent Successfully!");
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                textComplete.setText("");
                startActivity(intent);
            }
        });
        alert.show();



    }

    //Based on the Selected Group will return the ID of that group.
    //Use the loader because its already there???
    //Could try using when the spinner has a selection store what's currently selected's ID.
    //I assumed that getCursor will give us the currently selected item in the spinner
    private int getGroupID(){
        int groupID = Integer.parseInt(mAdapter.getCursor().getString(GroupListLoader.GROUP_ID));
        Log.i(DEBUG, "Group ID is: " + groupID);
        return groupID;
    }

    //Returns a list of all contacts (phone number, display name) based on group ID
    private ArrayList<Contact> getContacts(int groupID){
        ArrayList<Contact> contactList = new ArrayList<Contact>();

        //Query for all contacts_ids in that group
        Uri groupURI = ContactsContract.Data.CONTENT_URI;
        String[] projection = new String[] {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID };

        Cursor c = getContentResolver().query(
                groupURI,
                projection,
                ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID
                        + "=" + groupID, null, null);

        //For each Contact ID get the contact information
        while (c.moveToNext()) {
            String id = c
                    .getString(c
                            .getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID));

            //Projection will make it so only 1 Contact with any given name is returned.
            //For some reason contacts were returned multiple times due to slightly different meta
            //data
            Cursor pCur = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    new String[] { id },
                    null);

            Contact data;
            String name;
            String phoneNumber;
            //Save the Contact Data in a wrapper object then store it in an array
            while (pCur.moveToNext()) {
                name = pCur
                        .getString(pCur
                                .getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));

                phoneNumber = pCur
                        .getString(pCur
                                .getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));

                data = new Contact(phoneNumber, name);
                Log.i(DEBUG, data.toString());
                contactList.add(data);
            }
            pCur.close();
        }

        //If there are no contacts return null and break.
        if(contactList.size() == 0){return null;}
        return contactList;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_text_merge, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    //Creates the GroupListLoader and returns it
    public Loader onCreateLoader(int id, Bundle args) {
        return new GroupListLoader(this);
    }

    @Override
    //Swaps the loaded loader into an adapter so the user can see the groups and select one
    public void onLoadFinished(Loader loader, Cursor cursor) {
        mAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader loader) {
        mAdapter.swapCursor(null);
    }
}