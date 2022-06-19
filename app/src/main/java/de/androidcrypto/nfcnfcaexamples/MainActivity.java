package de.androidcrypto.nfcnfcaexamples;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    TextView nfcContentParsed, nfcContentRaw;

    private NfcAdapter mNfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nfcContentParsed = findViewById(R.id.tvMainNfcaContentParsed);
        nfcContentRaw = findViewById(R.id.tvMainNfcaContentRaw);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);


    }

    public boolean getUID(Tag tag, StringBuilder Uid) {
        NfcA mNfcA = NfcA.get(tag);

        if (mNfcA != null) {
            // The tag is NfcA capable
            try {
                mNfcA.connect();
                // Do a Read operation at page 0 an 1
                byte[] result = mNfcA.transceive(new byte[]{
                        (byte) 0x3A,  // FAST_READ
                        (byte) (0 & 0x0ff), // page 0
                        (byte) (1 & 0x0ff), // page 1
                });

                if (result == null) {
                    // either communication to the tag was lost or a NACK was received
                    // Log and return
                    return false;
                } else if ((result.length == 1) && ((result[0] & 0x00A) != 0x00A)) {
                    // NACK response according to Digital Protocol/T2TOP
                    // Log and return
                    return false;
                } else {
                    // success: response contains ACK or actual data
                    for (int i = 0; i < result.length; i++) {
                        // byte 4 is a check byte
                        if (i == 3) continue;
                        Uid.append(String.format("%02X ", result[i]));
                    }

                    // Close and return
                    try {
                        mNfcA.close();
                    } catch (IOException e) {

                    }
                    return true;
                }

            } catch (TagLostException e) {
                // Log and return
                return false;
            } catch (IOException e) {
                // Log and return
                return false;
            } finally {
                try {
                    mNfcA.close();
                } catch (IOException e) {

                }
            }
        } else {
            // Log error
            return false;
        }
    }

    // This method is run in another thread when a card is discovered
    // !!!! This method cannot cannot direct interact with the UI Thread
    // Use `runOnUiThread` method to change the UI from this method
    @Override
    public void onTagDiscovered(Tag tag) {
        // Read and or write to Tag here to the appropriate Tag Technology type class
        // in this example the card should be an Ndef Technology Type

        System.out.println("NFC tag discovered");

        NfcA nfcA = null;

        try {
            nfcA = NfcA.get(tag);

            if (nfcA != null) {
                runOnUiThread(() -> {
                    Toast.makeText(getApplicationContext(),
                            "NFC tag is Nfca compatible",
                            Toast.LENGTH_SHORT).show();
                });

                // Make a Sound
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(150, 10));
                } else {
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(200);
                }

                nfcA.connect();

                // check that the tag is a NTAG213/215/216 manufactured by NXP - stop if not
                String ntagVersion = NfcIdentifyNtag.checkNtagType(nfcA, tag.getId());
                if (ntagVersion.equals("0")) {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(),
                                "NFC tag is NOT of type NXP NTAG213/215/216",
                                Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                int nfcaMaxTranceiveLength = nfcA.getMaxTransceiveLength(); // important for the readFast command
                int ntagPages = NfcIdentifyNtag.getIdentifiedNtagPages();
                int ntagMemoryBytes = NfcIdentifyNtag.getIdentifiedNtagMemoryBytes();
                String nfcaContent = "raw data of " + NfcIdentifyNtag.getIdentifiedNtagType() + "\n" +
                        "number of pages: " + ntagPages +
                        " total memory: " + ntagMemoryBytes +
                        " bytes\n" +
                        "tag ID: " + bytesToHex(NfcIdentifyNtag.getIdentifiedNtagId()) + "\n";
                nfcaContent = nfcaContent + "maxTranceiveLength: " + nfcaMaxTranceiveLength + " bytes\n";
                // read the complete memory depending on ntag type
                byte[] ntagMemory = new byte[ntagMemoryBytes];
                // read the complete content of the tag on one run
                byte[] response;
                try {
                    // todo make a paging because maxTranceiveBytes is too low for a NTAG215/216 on a Samsung A5 with 253 bytes
                    // todo better read 144 bytes = 36 pages (= 0x24 in one run, this is the complete memory of a NTAG213
                    byte[] command = new byte[]{
                            (byte) 0x3A,  // FAST_READ
                            (byte) (0x04 & 0x0ff), // page 4 is the first user memory page
                            (byte) (0x27 & 0x0ff), // 0x04 + 0x23  // this is the last user page depending on ntag type
                    };
                    nfcaContent = nfcaContent + "command: " + bytesToHex(command) + "\n";
                    response = nfcA.transceive(command);
                    if (response == null) {
                        // either communication to the tag was lost or a NACK was received
                        // Log and return
                        nfcaContent = nfcaContent + "ERROR: null response";
                        String finalNfcaText = nfcaContent;
                        runOnUiThread(() -> {
                            nfcContentParsed.setText(finalNfcaText);
                            System.out.println(finalNfcaText);
                        });
                        return;
                    } else if ((response.length == 1) && ((response[0] & 0x00A) != 0x00A)) {
                        // NACK response according to Digital Protocol/T2TOP
                        // Log and return
                        nfcaContent = nfcaContent + "ERROR: NACK response: " + bytesToHex(response);
                        String finalNfcaText = nfcaContent;
                        runOnUiThread(() -> {
                            nfcContentParsed.setText(finalNfcaText);
                            System.out.println(finalNfcaText);
                        });
                        return;
                    } else {
                        // success: response contains ACK or actual data
                        nfcaContent = nfcaContent + "successful reading " +
                                response.length + " bytes\n";
                        nfcaContent = nfcaContent + bytesToHex(response) +
                                "\n";
                        // Close and return
                        /*
                        try {
                            nfcA.close();
                        } catch (IOException e) {

                        }*/

                    }
                } catch (TagLostException e) {
                    // Log and return
                    nfcaContent = nfcaContent + "ERROR: Tag lost exception";
                    String finalNfcaText = nfcaContent;
                    runOnUiThread(() -> {
                        nfcContentParsed.setText(finalNfcaText);
                        System.out.println(finalNfcaText);
                    });
                    return;
                } catch (IOException e) {

                    e.printStackTrace();

                }


                String finalNfcaText = nfcaContent;
                runOnUiThread(() -> {
                    nfcContentParsed.setText(finalNfcaText);
                    System.out.println(finalNfcaText);
                });
/*
            StringBuilder Uid = new StringBuilder();
            String contentRaw = "getUid from Tag: ";
            boolean successUid = getUID(tag, Uid);
            if (!successUid){
                // Not a successful read
                return;
            } else {
                // Feedback to user about successful read
                contentRaw = contentRaw + Uid + "\n";
                final String finalContentRaw = contentRaw;
                runOnUiThread(() -> {
                    nfcContentRaw.setText(finalContentRaw);
                    Toast.makeText(getApplicationContext(),
                            "NFC tag is Nfca compatible",
                            Toast.LENGTH_SHORT).show();
                });
            }
*/


            } else {
                runOnUiThread(() -> {
                    Toast.makeText(getApplicationContext(),
                            "NFC tag is NOT Nfca compatible",
                            Toast.LENGTH_SHORT).show();
                });
            }
        } catch (IOException e) {
            //Trying to catch any ioexception that may be thrown
            e.printStackTrace();
        } catch (Exception e) {
            //Trying to catch any exception that may be thrown
            e.printStackTrace();

        } finally {
            try {
                nfcA.close();
            } catch (IOException e) {
            }
        }
        /*
        Ndef mNdef = Ndef.get(tag);

        // Check that it is an Ndef capable card
        if (mNdef != null) {

            // If we want to read
            // As we did not turn on the NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
            // We can get the cached Ndef message the system read for us.

            NdefMessage mNdefMessage = mNdef.getCachedNdefMessage();

            // Make a vibration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(150,10));
            } else {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(200);
            }

            NdefRecord[] record = mNdefMessage.getRecords();
            String ndefContent = "raw data\n";
            int ndefRecordsCount = record.length;
            ndefContent = ndefContent + "nr of records: " + ndefRecordsCount + "\n";
            // Success if got to here
            runOnUiThread(() -> {
                Toast.makeText(getApplicationContext(),
                        "Read from NFC success, number of records: " + ndefRecordsCount,
                        Toast.LENGTH_SHORT).show();
            });

            // get uid of the tag
            ndefContent = ndefContent + "UID: " + bytesToHex(mNdef.getTag().getId()) + "\n";
            // get the techlist = support technoligies of the tag, e.g. android.nfc.tech.Nfca
            ndefContent = ndefContent + "TecList: " + Arrays.toString(mNdef.getTag().getTechList()) + "\n";

            if (ndefRecordsCount > 0) {
                String ndefText = "";
                for (int i = 0; i < ndefRecordsCount; i++) {
                    short ndefTnf = record[i].getTnf();

                    switch (ndefTnf) {
                        case NdefRecord.TNF_EMPTY: {
                            ndefContent = ndefContent + "\n" + "rec: " + i +
                                    " TNF: " + ndefTnf + " (0 TNF_EMPTY)";
                            break;
                        }
                        case NdefRecord.TNF_WELL_KNOWN: {
                            ndefContent = ndefContent + "\n" + "rec: " + i +
                                    " TNF: " + ndefTnf + " (1 TNF_WELL_KNOWN)";
                            break;
                        }
                        case NdefRecord.TNF_MIME_MEDIA: {
                            ndefContent = ndefContent + "\n" + "rec: " + i +
                                    " TNF: " + ndefTnf + " (2 TNF_MIME_MEDIA)";
                            break;
                        }
                        case NdefRecord.TNF_ABSOLUTE_URI: {
                            ndefContent = ndefContent + "\n" + "rec: " + i +
                                    " TNF: " + ndefTnf + " (3 TNF_ABSOLUTE_URI)";
                            break;
                        }
                        case NdefRecord.TNF_EXTERNAL_TYPE: {
                            ndefContent = ndefContent + "\n" + "rec: " + i +
                                    " TNF: " + ndefTnf + " (4 TNF_EXTERNAL_TYPE)";
                            break;
                        }
                        case NdefRecord.TNF_UNKNOWN: {
                            ndefContent = ndefContent + "\n" + "rec: " + i +
                                    " TNF: " + ndefTnf + " (5 TNF_UNKNOWN)";
                            break;
                        }
                        case NdefRecord.TNF_UNCHANGED: {
                            ndefContent = ndefContent + "\n" + "rec: " + i +
                                    " TNF: " + ndefTnf + " (6 TNF_UNCHANGED)";
                            break;
                        }
                        default: {
                            ndefContent = ndefContent + "\n" + "rec: " + i +
                                    " TNF: " + ndefTnf + " (undefined)";
                            break;
                        }
                    }

                    byte[] ndefType = record[i].getType();
                    byte[] ndefPayload = record[i].getPayload();

                    ndefContent = ndefContent + "\n" + "rec " + i + " inf: " + ndefTnf +
                            " type: " + bytesToHex(ndefType) +
                            " payload: " + bytesToHex(ndefPayload) +
                            " \n" + new String(ndefPayload) + " \n";
                    String finalNdefContent = ndefContent;
                    runOnUiThread(() -> {
                        nfcContentRaw.setText(finalNdefContent);
                        System.out.println(finalNdefContent);
                    });

                    // here we are trying to parse the content
                    // Well known type - Text
                    if (ndefTnf == NdefRecord.TNF_WELL_KNOWN &&
                            Arrays.equals(ndefType, NdefRecord.RTD_TEXT)) {
                        ndefText = ndefText + "\n" + "rec: " + i +
                                " Well known Text payload\n" + new String(ndefPayload) + " \n";
                        ndefText = ndefText + Utils.parseTextrecordPayload(ndefPayload) + " \n";
                    }
                    // Well known type - Uri
                    if (ndefTnf == NdefRecord.TNF_WELL_KNOWN &&
                            Arrays.equals(ndefType, NdefRecord.RTD_URI)) {
                        ndefText = ndefText + "\n" + "rec: " + i +
                                " Well known Uri payload\n" + new String(ndefPayload) + " \n";
                        ndefText = ndefText + Utils.parseUrirecordPayload(ndefPayload) + " \n";
                    }

                    // TNF 2 Mime Media
                    if (ndefTnf == NdefRecord.TNF_MIME_MEDIA) {
                        ndefText = ndefText + "\n" + "rec: " + i +
                                " TNF Mime Media  payload\n" + new String(ndefPayload) + " \n";
                        ndefText = ndefText + "TNF Mime Media  type\n" + new String(ndefType) + " \n";
                    }
                    // TNF 4 External type
                    if (ndefTnf == NdefRecord.TNF_EXTERNAL_TYPE) {
                        ndefText = ndefText + "\n" + "rec: " + i +
                                " TNF External type payload\n" + new String(ndefPayload) + " \n";
                        ndefText = ndefText + "TNF External type type\n" + new String(ndefType) + " \n";
                    }
                    String finalNdefText = ndefText;
                    runOnUiThread(() -> {
                        nfcContentParsed.setText(finalNdefText);
                        System.out.println(finalNdefText);
                    });
                } // for
            }
        } else {
            runOnUiThread(() -> {
                Toast.makeText(getApplicationContext(),
                        "mNdef is null",
                        Toast.LENGTH_SHORT).show();
            });
        }*/
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for (byte b : bytes) result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    private String getDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result + "";
    }

    private void showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mNfcAdapter != null) {

            if (!mNfcAdapter.isEnabled())
                showWirelessSettings();

            Bundle options = new Bundle();
            // Work around for some broken Nfc firmware implementations that poll the card too fast
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);

            // Enable ReaderMode for all types of card and disable platform sounds
            // the option NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK is NOT set
            // to get the data of the tag afer reading
            mNfcAdapter.enableReaderMode(this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NFC_B |
                            NfcAdapter.FLAG_READER_NFC_F |
                            NfcAdapter.FLAG_READER_NFC_V |
                            NfcAdapter.FLAG_READER_NFC_BARCODE |
                            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null)
            mNfcAdapter.disableReaderMode(this);
    }
}