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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class MainActivityOld2 extends AppCompatActivity implements NfcAdapter.ReaderCallback {

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
                String tagIdString = getDec(tag.getId());
                String nfcaContent = "raw data of " + NfcIdentifyNtag.getIdentifiedNtagType() + "\n" +
                        "number of pages: " + ntagPages +
                        " total memory: " + ntagMemoryBytes +
                        " bytes\n" +
                        "tag ID: " + bytesToHex(NfcIdentifyNtag.getIdentifiedNtagId()) + "\n" +
                        "tag ID: " + tagIdString + "\n";
                nfcaContent = nfcaContent + "maxTranceiveLength: " + nfcaMaxTranceiveLength + " bytes\n";
                // read the complete memory depending on ntag type
                byte[] ntagMemory = new byte[ntagMemoryBytes];
                // read the content of the tag in several runs
                byte[] response;
                try {
                    // one read will read 4 pages of each 4 bytes = 16 bytes
                    int nfcaReadFullRounds = ntagMemoryBytes / 16; // 55
                    int nfcaReadFullRoundsTotalBytes = nfcaReadFullRounds * 16; // 880 bytes
                    int nfcaReadModuloRoundsTotalBytes = ntagMemoryBytes - nfcaReadFullRoundsTotalBytes; // 8 bytes
                    nfcaContent = nfcaContent + "nfcaReadFullRounds: " + nfcaReadFullRounds + "\n";
                    nfcaContent = nfcaContent + "nfcaReadFullRoundsTotalBytes: " + nfcaReadFullRoundsTotalBytes + "\n";
                    nfcaContent = nfcaContent + "nfcaReadModuloRoundsTotalBytes: " + nfcaReadModuloRoundsTotalBytes + "\n";
                    // do the full readings
                    for (int i = 0; i < nfcaReadFullRounds; i++) {
                        byte[] command = new byte[]{
                                (byte) 0x30,  // READ
                                (byte) ((4 + (i * 4)) & 0x0ff), // page 4 is the first user memory page
                        };
                        nfcaContent = nfcaContent + "i: " + i + " command: " + bytesToHex(command) + "\n";
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
                            // copy the response to the ntagMemory
                            System.arraycopy(response, 0, ntagMemory, (16 * i), 16);
                        }
                    }
                    nfcaContent = nfcaContent + "full reading complete: " + "\n" + bytesToHex(ntagMemory) + "\n";
                    nfcaContent = nfcaContent + "start reading of the last bytes: "+  "\n";

                    byte[] command = new byte[]{
                            (byte) 0x30,  // READ
                            (byte) ((4 + (nfcaReadFullRounds * 4)) & 0x0ff), // page 4 is the first user memory page
                    };
                    nfcaContent = nfcaContent + "i: " + nfcaReadFullRounds + " command: " + bytesToHex(command) + "\n";
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
                        // copy the response to the ntagMemory
                        // beware that the last response recieves to many bytes
                        // NTAG216 does have 888 bytes memomory but we already read
                        // 55 * 16 = 880 bytes, so we should copy 8 bytes only
                        System.arraycopy(response, 0, ntagMemory, (16 * nfcaReadFullRounds), (ntagMemoryBytes - nfcaReadFullRoundsTotalBytes));
                    }
                    nfcaContent = nfcaContent + "full content: " + "\n" + bytesToHex(ntagMemory) + "\n";
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