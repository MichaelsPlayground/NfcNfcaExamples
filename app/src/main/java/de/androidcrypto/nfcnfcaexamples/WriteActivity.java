package de.androidcrypto.nfcnfcaexamples;

import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.NfcA;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class WriteActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    com.google.android.material.textfield.TextInputLayout inputFieldDecoration;
    com.google.android.material.textfield.TextInputEditText inputField, resultNfcWriting;

    TextView nfcContentParsed, nfcContentRaw;
    Button sample1, sample2;
    private NfcAdapter mNfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write);
        inputField = findViewById(R.id.etWriteInputField);
        inputFieldDecoration = findViewById(R.id.etWriteInputFieldDecoration);
        nfcContentParsed = findViewById(R.id.tvWriteNfcaContentParsed);
        nfcContentRaw = findViewById(R.id.tvWriteNfcaContentRaw);
        sample1 = findViewById(R.id.btnWriteSample1);
        sample2 = findViewById(R.id.btnWriteSample2);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        sample1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                inputField.setText("Some testdata to write on the tag");
            }
        });

        sample2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                inputField.setText("Some more testdata to write on the tag");
            }
        });
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
                writeToUiToast("NFC tag is Nfca compatible");

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
                    writeToUiAppend(nfcContentRaw, "NFC tag is NOT of type NXP NTAG213/215/216");
                    writeToUiToast("NFC tag is NOT of type NXP NTAG213/215/216");
                    return;
                }

                int nfcaMaxTranceiveLength = nfcA.getMaxTransceiveLength(); // important for the readFast command
                int ntagPages = NfcIdentifyNtag.getIdentifiedNtagPages();
                int ntagMemoryBytes = NfcIdentifyNtag.getIdentifiedNtagMemoryBytes();
                String tagIdString = Utils.getDec(tag.getId());
                String nfcaContent = "raw data of " + NfcIdentifyNtag.getIdentifiedNtagType() + "\n" +
                        "number of pages: " + ntagPages +
                        " total memory: " + ntagMemoryBytes +
                        " bytes\n" +
                        "tag ID: " + Utils.bytesToHex(NfcIdentifyNtag.getIdentifiedNtagId()) + "\n" +
                        "tag ID: " + tagIdString + "\n";
                nfcaContent = nfcaContent + "maxTranceiveLength: " + nfcaMaxTranceiveLength + " bytes\n";
                // read the complete memory depending on ntag type
                byte[] ntagMemory = new byte[ntagMemoryBytes];
                // read the content of the tag in several runs
                byte[] response;
                try {
                    // get data from InputField
                    String dataString = inputField.getText().toString();
                    byte[] dataByte = dataString.getBytes(StandardCharsets.UTF_8);
                    int dataLength = dataByte.length;
                    // as the Tag is saving in blocks of 4 bytes we need to know how many pages we do need
                    int dataPages = dataLength / 4;
                    int dataPagesMod = dataLength % 4; // if there is a remainder we need to use a new page to write
                    nfcaContent = nfcaContent + "data length: " + dataLength + "\n";
                    nfcaContent = nfcaContent + "data: " + Utils.bytesToHex(dataByte) + "\n";
                    nfcaContent = nfcaContent + "dataPages: " + dataPages + "\n";
                    nfcaContent = nfcaContent + "dataPagesMod: " + dataPagesMod + "\n";

                    // check that the data is fitting on the tag
                    if (dataLength > ntagMemoryBytes) {
                        writeToUiAppend(nfcContentRaw, "data in InputField is too long for tag");
                        writeToUiToast("data in InputField is too long for tag");
                        System.out.println("data in InputField is too long for tag");
                        return;
                    }
                    nfcaContent = nfcaContent + "writing full pages" + "\n";
                    // writing full pages of 4 bytes each
                    for (int i = 0; i < dataPages; i++) {
                        System.out.println("starting round: " + i);
                        byte[] commandW;
                        commandW = new byte[]{
                                (byte) 0xA2,  // WRITE
                                (byte) ((4 + i) & 0x0ff), // page 4 is the first user memory page
                                dataByte[0 + (i * 4)],
                                dataByte[1 + (i * 4)],
                                dataByte[2 + (i * 4)],
                                dataByte[3 + (i * 4)]
                        };
                        nfcaContent = nfcaContent + "command: " + Utils.bytesToHex(commandW) + "\n";
                        response = nfcA.transceive(commandW);
                        if (response == null) {
                            // either communication to the tag was lost or a NACK was received
                            // Log and return
                            nfcaContent = nfcaContent + "ERROR: null response";
                            String finalNfcaText = nfcaContent;
                            writeToUiAppend(nfcContentRaw, finalNfcaText);
                            System.out.println(finalNfcaText);
                            return;
                        } else if ((response.length == 1) && ((response[0] & 0x00A) != 0x00A)) {
                            // NACK response according to Digital Protocol/T2TOP
                            // Log and return
                            nfcaContent = nfcaContent + "ERROR: NACK response: " + Utils.bytesToHex(response);
                            String finalNfcaText = nfcaContent;
                            writeToUiAppend(nfcContentRaw, finalNfcaText);
                            System.out.println(finalNfcaText);
                            return;
                        } else {
                            // success: response contains ACK or actual data
                            // nfcaContent = nfcaContent + "successful reading " + response.length + " bytes\n";
                            // nfcaContent = nfcaContent + bytesToHex(response) + "\n";
                            // copy the response to the ntagMemory
                            //nfcaContent = nfcaContent + "number of bytes read: : " + response.length + "\n";
                            nfcaContent = nfcaContent + "response:\n" + Utils.bytesToHex(response) + "\n";
                            //System.arraycopy(response, 0, ntagMemory, (nfcaMaxTranceive4ByteLength * i), nfcaMaxTranceive4ByteLength);
                        }

                    }

                    // ### section for writing only part of page
                    if (dataPagesMod == 0) {
                        // don't write a new page
                        try {
                            nfcA.close();
                        } catch (IOException e) {
                        }
                        return;
                    }
                    byte[] commandW = new byte[0];
                    if (dataPagesMod == 1) {
                        commandW = new byte[]{
                                (byte) 0xA2,  // WRITE
                                (byte) ((4 + dataPages) & 0x0ff), // page 4 is the first user memory page
                                dataByte[0 + (dataPages * 4)],
                                (byte) 0x00,
                                (byte) 0x00,
                                (byte) 0x00
                        };
                    }
                    if (dataPagesMod == 2) {
                        commandW = new byte[]{
                                (byte) 0xA2,  // WRITE
                                (byte) ((4 + dataPages) & 0x0ff), // page 4 is the first user memory page
                                dataByte[0 + (dataPages * 4)],
                                dataByte[1 + (dataPages * 4)],
                                (byte) 0x00,
                                (byte) 0x00
                        };
                    }
                    if (dataPagesMod == 3) {
                        commandW = new byte[]{
                                (byte) 0xA2,  // WRITE
                                (byte) ((4 + dataPages) & 0x0ff), // page 4 is the first user memory page
                                dataByte[0 + (dataPages * 4)],
                                dataByte[1 + (dataPages * 4)],
                                dataByte[2 + (dataPages * 4)],
                                (byte) 0x00
                        };
                    }

                    nfcaContent = nfcaContent + "command: " + Utils.bytesToHex(commandW) + "\n";
                    response = nfcA.transceive(commandW);
                    if (response == null) {
                        // either communication to the tag was lost or a NACK was received
                        // Log and return
                        nfcaContent = nfcaContent + "ERROR: null response";
                        String finalNfcaText = nfcaContent;
                        writeToUiAppend(nfcContentRaw, finalNfcaText);
                        System.out.println(finalNfcaText);
                        return;
                    } else if ((response.length == 1) && ((response[0] & 0x00A) != 0x00A)) {
                        // NACK response according to Digital Protocol/T2TOP
                        // Log and return
                        nfcaContent = nfcaContent + "ERROR: NACK response: " + Utils.bytesToHex(response);
                        String finalNfcaText = nfcaContent;
                        writeToUiAppend(nfcContentRaw, finalNfcaText);
                        System.out.println(finalNfcaText);
                        return;
                    } else {
                        // success: response contains ACK or actual data
                        // nfcaContent = nfcaContent + "successful reading " + response.length + " bytes\n";
                        // nfcaContent = nfcaContent + bytesToHex(response) + "\n";
                        // copy the response to the ntagMemory
                        //nfcaContent = nfcaContent + "number of bytes read: : " + response.length + "\n";
                        nfcaContent = nfcaContent + "response:\n" + Utils.bytesToHex(response) + "\n";
                        //System.arraycopy(response, 0, ntagMemory, (nfcaMaxTranceive4ByteLength * i), nfcaMaxTranceive4ByteLength);
                    }

                } catch (TagLostException e) {
                    // Log and return
                    nfcaContent = nfcaContent + "ERROR: Tag lost exception";
                    String finalNfcaText = nfcaContent;
                    writeToUiAppend(nfcContentRaw, finalNfcaText);
                    System.out.println(finalNfcaText);
                    return;
                } catch (IOException e) {

                    e.printStackTrace();

                }
                String finalNfcaRawText = nfcaContent;
                String finalNfcaText = "parsed content:\n" + new String(ntagMemory, StandardCharsets.US_ASCII);
                writeToUiAppend(nfcContentRaw, finalNfcaText);
                writeToUiAppend(nfcContentParsed, finalNfcaText);
                System.out.println(finalNfcaText);
            } else {
                writeToUiToast("NFC tag is NOT Nfca compatible");
            }
        } catch (
                IOException e) {
            writeToUiAppend(nfcContentRaw, "IOException: " + e);
            //Trying to catch any ioexception that may be thrown
            e.printStackTrace();
        } catch (
                Exception e) {
            //Trying to catch any exception that may be thrown
            e.printStackTrace();
            writeToUiAppend(nfcContentRaw, "Exception: " + e);
        } finally {
            try {
                nfcA.close();
            } catch (IOException e) {
                writeToUiAppend(nfcContentRaw, "IOException: " + e);
            }
        }

    }

    private void showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    private void writeToUiAppend(TextView textView, String message) {
        runOnUiThread(() -> {
            String newString = message + "\n" + textView.getText().toString();
            textView.setText(newString);
        });
    }

    private void writeToUiToast(String message) {
        runOnUiThread(() -> {
            Toast.makeText(getApplicationContext(),
                    message,
                    Toast.LENGTH_SHORT).show();
        });
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