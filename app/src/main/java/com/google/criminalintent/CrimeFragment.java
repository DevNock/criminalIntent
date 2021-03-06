package com.google.criminalintent;



import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import java.io.File;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import android.support.v7.app.ActionBar;
import android.widget.ImageButton;
import android.widget.ImageView;

/**
 * Created by Sergey on 23.11.2015.
 */
public class CrimeFragment extends Fragment {

    public static final String EXTRA_CRIME_ID =
            "com.google.android.criminalintent.crime_id";

    private static final String TAG = "CrimeFragment";
    private static final String DIALOG_IMAGE = "image";

    private static final String DIALOG_DATE = "date";
    private static final String DIALOG_TIME = "time";
    private static final int REQUEST_DATE = 0;
    private static final int REQUEST_PHOTO = 1;
    private static final int REQUEST_CONTACT = 2;

    private static final int DELETE_MENU = 0;

    private Crime crime;
    private EditText titleField;
    private Button crimeDateButton;
    private Button suspectButton;
    private Button reportButton;
    private CheckBox crimeSolvedCheckBox;
    private ImageButton photoButton;
    private ImageView photoView;
    private Callbacks callbacks;


    private static final int DELETE_RESULT = 0;
    private static final int SAVE_RESULT = 1;

    private int result;

    public interface Callbacks {
        void onCrimeUpdated(Crime crime);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        UUID crimeID = (UUID)getArguments().getSerializable(EXTRA_CRIME_ID);
        crime = CrimeLab.get(getActivity()).getCrime(crimeID);
        result = SAVE_RESULT;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        callbacks = (Callbacks)context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callbacks = null;
    }

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_crime, container, false);

        titleField = (EditText) view.findViewById(R.id.crimeTitle);
        titleField.setText(crime.getTitle());
        ((AppCompatActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        titleField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                crime.setTitle(s.toString());
                callbacks.onCrimeUpdated(crime);
                getActivity().setTitle(crime.getTitle());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        crimeDateButton = (Button)view.findViewById(R.id.crimeDateButton);
        //DateFormat format = DateFormat.getDateInstance();
        updateDate();
        crimeDateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager fm = getActivity().getSupportFragmentManager();
                DatePickerFragment dialog = DatePickerFragment.newInstance(crime.getDate());
                dialog.setTargetFragment(CrimeFragment.this, REQUEST_DATE);
                dialog.show(fm, DIALOG_DATE);


            }
        });

        crimeSolvedCheckBox = (CheckBox)view.findViewById(R.id.crimeSolvedCheckBox);
        crimeSolvedCheckBox.setChecked(crime.isSolved());
        crimeSolvedCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                crime.setSolved(isChecked);
                callbacks.onCrimeUpdated(crime);
            }
        });

        photoButton = (ImageButton) view.findViewById(R.id.crime_imageButton);
        photoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), CrimeCameraActivity.class);
                startActivityForResult(intent, REQUEST_PHOTO);
            }
        });

        photoView = (ImageView)view.findViewById(R.id.crime_imageView);
        photoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Photo photo = crime.getPhoto();
                if(photo == null){
                    return;
                }

                FragmentManager fm = getActivity()
                        .getSupportFragmentManager();
                String path = getActivity().getFileStreamPath(photo.getFileName())
                        .getAbsolutePath();
                ImageFragment.newInstance(path)
                        .show(fm, DIALOG_IMAGE);
            }
        });

        suspectButton = (Button) view.findViewById(R.id.crime_suspectButton);
        suspectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK,
                        ContactsContract.Contacts.CONTENT_URI);
                startActivityForResult(intent, REQUEST_CONTACT);
            }
        });

        if(crime.getSuspect() != null){
            suspectButton.setText(crime.getSuspect());
        }

        reportButton = (Button) view.findViewById(R.id.crime_reportButton);
        reportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plane");
                intent.putExtra(Intent.EXTRA_TEXT, getCrimeReport());
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crime_report_subject));
                intent = Intent.createChooser(intent, getString(R.string.send_report));
                startActivity(intent);
            }
        });

        registerForContextMenu(photoView);

        return view;
    }

    private void showPhoto(){
        Photo photo = crime.getPhoto();
        BitmapDrawable bitmap = null;
        if(photo != null){
            String path = getActivity()
                    .getFileStreamPath(photo.getFileName()).getAbsolutePath();
            bitmap = PictureUtils.getScaledDrawable(getActivity(), path);
        }
        photoView.setImageDrawable(bitmap);
    }

    @Override
    public void onStart() {
        super.onStart();
        showPhoto();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_crime, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.fragment_crime_menu_delete:
                result = DELETE_RESULT;
                getActivity().finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        switch(v.getId()){
            case R.id.crime_imageView:
                menu.add(0, DELETE_MENU, 0, getResources().getString(R.string.delete_photo));
                return;
            default:
                super.onCreateContextMenu(menu, v, menuInfo);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case DELETE_MENU:
                if(crime.getPhoto() != null){
                    String path = getActivity().getFileStreamPath(crime.getPhoto().getFileName())
                            .getAbsolutePath();
                    File file = new File(path);
                    file.delete();
                    crime.setPhoto(null);
                    photoView.setImageDrawable(null);
                }
        }
        return super.onContextItemSelected(item);
    }

    private void updateDate(){
        String format = "EEEE, d MMMM, yyyy";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
        crimeDateButton.setText(simpleDateFormat.format(crime.getDate()).toString());
    }

    public static Fragment newInstance(UUID crimeID){
        Bundle args = new Bundle();
        args.putSerializable(EXTRA_CRIME_ID, crimeID);
        CrimeFragment fragment = new CrimeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getCrimeReport(){
        String solvedString = null;
        if(crime.isSolved()){
            solvedString = getString(R.string.crime_report_solved);
        } else{
            solvedString = getString(R.string.crime_report_unsolved);
        }

        String dateFormat = "EEE, MMM dd";
        String dateString = DateFormat.format(dateFormat, crime.getDate()).toString();

        String suspect = crime.getSuspect();
        if(suspect == null){
            suspect = getString(R.string.crime_report_no_suspect);
        } else{
            suspect = getString(R.string.crime_report_suspect, suspect);
        }

        String report = getString(R.string.crime_report,
                crime.getTitle(), dateString, solvedString, suspect);
        return report;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode != Activity.RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case REQUEST_DATE:
                Date date = (Date) data.getSerializableExtra(DatePickerFragment.EXTRA_DATE);
                crime.setDate(date);
                callbacks.onCrimeUpdated(crime);
                updateDate();
                return;
            case REQUEST_PHOTO:
                String fileName = (String) data.getSerializableExtra(CrimeCameraFragment.EXTRA_PHOTO_FILENAME);
                if(fileName != null){
                    if(crime.getPhoto() != null){
                        String path = getActivity().getFileStreamPath(crime.getPhoto().getFileName())
                                .getAbsolutePath();
                        File file = new File(path);
                        file.delete();
                    }
                    Photo photo = new Photo(fileName);
                    crime.setPhoto(photo);
                    callbacks.onCrimeUpdated(crime);
                    showPhoto();
                }
                return;
            case REQUEST_CONTACT:
                Uri contactUri = data.getData();
                String[] queryFields = new String[]{
                        ContactsContract.Contacts.DISPLAY_NAME
                };
                Cursor cursor = getActivity().getContentResolver()
                        .query(contactUri, queryFields, null, null, null);

                if(cursor.getCount() == 0){
                    cursor.close();
                    return;
                }

                cursor.moveToFirst();
                String suspect = cursor.getString(0);
                crime.setSuspect(suspect);
                callbacks.onCrimeUpdated(crime);
                suspectButton.setText(suspect);
                cursor.close();

        }

    }

    @Override
    public void onStop() {
        super.onStop();
        PictureUtils.cleanImageView(photoView);
    }

    @Override
    public void onPause() {
        super.onPause();
        switch (result) {
            case SAVE_RESULT:
                CrimeLab.get(getActivity()).saveCrimes();
                return;
            case DELETE_RESULT:
                CrimeLab.get(getActivity()).deleteCrime(crime);
                return;
            default:
                break;
        }
    }
}
