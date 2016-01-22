package mst.nsh9b3.uface;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Used for setting up and maintaining sharedpreferences.
 */
public class Options extends Activity
{
    // Current class description for log events
    private static final String TAG = "uFace::Options";

    // The application's shared preferences and an editor to change them
    public static SharedPreferences sharedPref;
    public static SharedPreferences.Editor sharedPrefEdit;

    // Name of the file containing the preferences
    public static final String SHARED_PREF = "sharedPreferences";

    // The EditTexts boxes used in this class
    EditText FTPAddressEditText;
    EditText FTPPortEditText;
    EditText FTPUsernameEditText;
    EditText FTPPasswordEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);

        // Get the EditText Views
        FTPAddressEditText = (EditText)findViewById(R.id.uFace_Options_FTPAddress);
        FTPPortEditText = (EditText)findViewById(R.id.uFace_Options_FTPPort);
        FTPUsernameEditText = (EditText)findViewById(R.id.uFace_Options_FTPUsername);
        FTPPasswordEditText = (EditText)findViewById(R.id.uFace_Options_FTPPassword);

        // Setup the sharedPreferences
        setupSharedPref();
    }

    /**
     * Properly grabs any stored sharedPreferences and sets the editText boxes to this value.
     * If there is no such stored sharedPreferences, a default value is provided.
     */
    private void setupSharedPref()
    {
        // Get the sharedPreferences and setup an editor
        sharedPref = this.getSharedPreferences(SHARED_PREF, MODE_PRIVATE);

        // Get saved values
        String FTPAddress = sharedPref.getString(getString(R.string.uFace_sharedPrefs_FTPAddress), getString(R.string.uFace_sharedPrefs_FTPAddress));
        String FTPPort = sharedPref.getString(getString(R.string.uFace_sharedPrefs_FTPPort), getString(R.string.uFace_sharedPrefs_FTPPort));
        String FTPUsername = sharedPref.getString(getString(R.string.uFace_sharedPrefs_FTPUsername), getString(R.string.uFace_sharedPrefs_FTPUsername));
        String FTPPassword = sharedPref.getString(getString(R.string.uFace_sharedPrefs_FTPPassword), getString(R.string.uFace_sharedPrefs_FTPPassword));

        // Set the text boxes in Options with the shared preference values
        FTPAddressEditText.setText(FTPAddress);
        FTPPortEditText.setText(FTPPort);
        FTPUsernameEditText.setText(FTPUsername);
        FTPPasswordEditText.setText(FTPPassword);
    }

    /**
     * Save the values in the EditText boxes into the sharedPreferences.
     * @param view the selected view
     */
    public void onClickSaveOptions(View view)
    {
        // Get the values stored in the EditText boxes
        String FTPAddress = FTPAddressEditText.getText().toString();
        String FTPPort = FTPPortEditText.getText().toString();
        String FTPUsername = FTPUsernameEditText.getText().toString();
        String FTPPassword = FTPPasswordEditText.getText().toString();

        // Get the sharedPreferences and setup an editor
        sharedPref = this.getSharedPreferences(SHARED_PREF, MODE_PRIVATE);
        sharedPrefEdit = sharedPref.edit();

        // Update the sharedPreferences
        sharedPrefEdit.putString(getString(R.string.uFace_sharedPrefs_FTPAddress), FTPAddress);
        sharedPrefEdit.putString(getString(R.string.uFace_sharedPrefs_FTPPort), FTPPort);
        sharedPrefEdit.putString(getString(R.string.uFace_sharedPrefs_FTPUsername), FTPUsername);
        sharedPrefEdit.putString(getString(R.string.uFace_sharedPrefs_FTPPassword), FTPPassword);
        sharedPrefEdit.apply();

        Toast.makeText(this, "Saved SharedPreferences", Toast.LENGTH_SHORT).show();
    }

    /**
     * Resets the values in the sharedPreferences to default as well as reset each of the EditText
     * boxes.
     * @param view the selected view
     */
    public void onClickResetOptions(View view)
    {
        // Get the sharedPreferences and setup an editor
        sharedPref = this.getSharedPreferences(SHARED_PREF, MODE_PRIVATE);
        sharedPrefEdit = sharedPref.edit();

        // Get the String values once from R.strings
        String FTPAddress = getString(R.string.uFace_sharedPrefs_FTPAddress);
        String FTPPort = getString(R.string.uFace_sharedPrefs_FTPPort);
        String FTPUsername = getString(R.string.uFace_sharedPrefs_FTPUsername);
        String FTPPassword = getString(R.string.uFace_sharedPrefs_FTPPassword);

        // Update the sharedPreferences to be the default values
        sharedPrefEdit.putString(FTPAddress, FTPAddress);
        sharedPrefEdit.putString(FTPPort, FTPPort);
        sharedPrefEdit.putString(FTPUsername, FTPUsername);
        sharedPrefEdit.putString(FTPPassword, FTPPassword);
        sharedPrefEdit.apply();

        // Set the EditText boxes in Options with the shared preference values
        FTPAddressEditText.setText(FTPAddress);
        FTPPortEditText.setText(FTPPort);
        FTPUsernameEditText.setText(FTPUsername);
        FTPPasswordEditText.setText(FTPPassword);

        Toast.makeText(this, "Reset SharedPreferences", Toast.LENGTH_SHORT).show();
    }
}
