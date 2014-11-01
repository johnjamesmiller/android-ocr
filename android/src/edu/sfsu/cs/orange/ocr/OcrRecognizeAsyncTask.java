/*
 * Copyright 2011 Robert Theis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.sfsu.cs.orange.ocr;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.tesseract.android.TessBaseAPI;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Class to send OCR requests to the OCR engine in a separate thread, send a success/failure message,
 * and dismiss the indeterminate progress dialog box. Used for non-continuous mode OCR only.
 */
final class OcrRecognizeAsyncTask extends AsyncTask<Void, Void, Boolean> {

  //  private static final boolean PERFORM_FISHER_THRESHOLDING = false; 
  //  private static final boolean PERFORM_OTSU_THRESHOLDING = false; 
  //  private static final boolean PERFORM_SOBEL_THRESHOLDING = false; 

  private static final String LOG_TAG = "OcrRecognizeAsyncTask";
  private CaptureActivity activity;
  private TessBaseAPI baseApi;
  private byte[] data;
  private int width;
  private int height;
  private OcrResult ocrResult;
  private long timeRequired;

  OcrRecognizeAsyncTask(CaptureActivity activity, TessBaseAPI baseApi, byte[] data, int width, int height) {
    this.activity = activity;
    this.baseApi = baseApi;
    this.data = data;
    this.width = width;
    this.height = height;
  }

  @Override
  protected Boolean doInBackground(Void... arg0) {
    long start = System.currentTimeMillis();
    Bitmap bitmap = activity.getCameraManager().buildLuminanceSource(data, width, height).renderCroppedGreyscaleBitmap();
    String textResult;

    //      if (PERFORM_FISHER_THRESHOLDING) {
    //        Pix thresholdedImage = Thresholder.fisherAdaptiveThreshold(ReadFile.readBitmap(bitmap), 48, 48, 0.1F, 2.5F);
    //        Log.e("OcrRecognizeAsyncTask", "thresholding completed. converting to bmp. size:" + bitmap.getWidth() + "x" + bitmap.getHeight());
    //        bitmap = WriteFile.writeBitmap(thresholdedImage);
    //      }
    //      if (PERFORM_OTSU_THRESHOLDING) {
    //        Pix thresholdedImage = Binarize.otsuAdaptiveThreshold(ReadFile.readBitmap(bitmap), 48, 48, 9, 9, 0.1F);
    //        Log.e("OcrRecognizeAsyncTask", "thresholding completed. converting to bmp. size:" + bitmap.getWidth() + "x" + bitmap.getHeight());
    //        bitmap = WriteFile.writeBitmap(thresholdedImage);
    //      }
    //      if (PERFORM_SOBEL_THRESHOLDING) {
    //        Pix thresholdedImage = Thresholder.sobelEdgeThreshold(ReadFile.readBitmap(bitmap), 64);
    //        Log.e("OcrRecognizeAsyncTask", "thresholding completed. converting to bmp. size:" + bitmap.getWidth() + "x" + bitmap.getHeight());
    //        bitmap = WriteFile.writeBitmap(thresholdedImage);
    //      }

    try {     
      baseApi.setImage(ReadFile.readBitmap(bitmap));
      String rawOCRResult = baseApi.getUTF8Text(); 

      //filter junk you can configure tess two to only recognize certain characters but it is very agressive and will find a lot more wrong characters
//      rawOCRResult = rawOCRResult.replaceAll("[^A-Z0-9]"," ");
      
      String initialResult = findIntial(rawOCRResult);
      String numberResult = findNumber(rawOCRResult);
      
      textResult = initialResult + numberResult + "\nall found text: " + rawOCRResult;
      String fileName = start + "_textResult_"+ textResult.replaceAll("[^A-Za-z0-9]","_") + ".png";
      saveImage(bitmap, fileName);
      
//      DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
//        @Override
//        public void onClick(DialogInterface dialog, int which) {
//          switch (which) {
//          case DialogInterface.BUTTON_POSITIVE:
//            // Yes button clicked
//            break;
//
//          case DialogInterface.BUTTON_NEGATIVE:
//            // No button clicked
//            break;
//          }
//        }
//      };
//
//      AlertDialog.Builder builder = new AlertDialog.Builder(this.activity);
//      builder.setMessage("Is " + initialResult + " " + numberResult + " correct?").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();
//      
      timeRequired = System.currentTimeMillis() - start;

      // Check for failure to recognize text
      if (textResult == null || textResult.equals("")) {
        return false;
      }
      ocrResult = new OcrResult();
      ocrResult.setWordConfidences(baseApi.wordConfidences());
      ocrResult.setMeanConfidence( baseApi.meanConfidence());
      ocrResult.setRegionBoundingBoxes(baseApi.getRegions().getBoxRects());
      ocrResult.setTextlineBoundingBoxes(baseApi.getTextlines().getBoxRects());
      ocrResult.setWordBoundingBoxes(baseApi.getWords().getBoxRects());
      ocrResult.setStripBoundingBoxes(baseApi.getStrips().getBoxRects());
      //ocrResult.setCharacterBoundingBoxes(baseApi.getCharacters().getBoxRects());
    } catch (RuntimeException e) {
      Log.e("OcrRecognizeAsyncTask", "Caught RuntimeException in request to Tesseract. Setting state to CONTINUOUS_STOPPED.");
      e.printStackTrace();
      try {
        baseApi.clear();
        activity.stopHandler();
      } catch (NullPointerException e1) {
        // Continue
      }
      return false;
    }
    timeRequired = System.currentTimeMillis() - start;
    ocrResult.setBitmap(bitmap);
    ocrResult.setText(textResult);
    ocrResult.setRecognitionTimeRequired(timeRequired);
    return true;
  }

  private void saveImage(Bitmap bitmap, String fileName) {
    try {
      File album = getAlbumStorageDir("OCRTest");
      
      File fileToSave = new File(album, fileName);
      FileOutputStream outputStream = new FileOutputStream(fileToSave);
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
      outputStream.close();
    } catch (FileNotFoundException e) {
      Log.e("OcrRecognizeAsyncTask", "Caught FileNotFoundException: "  + fileName );
      e.printStackTrace();
    } catch (IOException e) {
      Log.e("OcrRecognizeAsyncTask", "Caught IOException: "  + fileName );
      e.printStackTrace();
    }
  }
  
  private File getAlbumStorageDir(String albumName) {
	    // Get the directory for the user's public pictures directory. 
	    File file = new File(Environment.getExternalStoragePublicDirectory(
	            Environment.DIRECTORY_PICTURES), albumName);
	    if (!file.mkdirs()) {
	        Log.e(LOG_TAG, "Directory not created");
	    }
	    return file;
	}

private String findNumber(String rawOCRResult) {
	String numberResult = "";
      List<String> possibleNumberMatches = new ArrayList<String>();
      Matcher  numberMatcher = Pattern.compile("[0-9]{4,6}").matcher(rawOCRResult);
      
      while (numberMatcher.find()){
    	  possibleNumberMatches.add(numberMatcher.group());
      }
      if( possibleNumberMatches.size() < 1){
    	  numberResult += "No number\n";
      }else if( possibleNumberMatches.size() == 1 ){
    	  numberResult += possibleNumberMatches.get(0);
      }else if( possibleNumberMatches.size() > 1 ){
    	  numberResult += "multiple possible number matches: \n";
    	  for (String possibleNumber : possibleNumberMatches) {
    		  numberResult += possibleNumber + "\n";
    	  }
      }
	return numberResult;
}

private String findIntial(String rawOCRResult) {
	List<String> possibleInitialMatches = new ArrayList<String>();
      Matcher  initialMatcher = Pattern.compile("[A-Z]{2,4}").matcher(rawOCRResult);

      while (initialMatcher.find()){
    	  possibleInitialMatches.add(initialMatcher.group());
      }
      String initialResult = "";
      
      if( possibleInitialMatches.size() < 1){
    	  initialResult += "No Initial\n";
      }else if( possibleInitialMatches.size() == 1 ){
    	  initialResult += possibleInitialMatches.get(0) + " ";
      }else if( possibleInitialMatches.size() > 1 ){
    	  initialResult += "multiple possible initial matches: \n";
    	  for (String possibleInitial : possibleInitialMatches) {
    		  initialResult += possibleInitial + "\n";
    	  }
      }
	return initialResult;
}

  @Override
  protected void onPostExecute(Boolean result) {
    super.onPostExecute(result);

    Handler handler = activity.getHandler();
    if (handler != null) {
      // Send results for single-shot mode recognition.
      if (result) {
        Message message = Message.obtain(handler, R.id.ocr_decode_succeeded, ocrResult);
        message.sendToTarget();
      } else {
        Message message = Message.obtain(handler, R.id.ocr_decode_failed, ocrResult);
        message.sendToTarget();
      }
      activity.getProgressDialog().dismiss();
    }
    if (baseApi != null) {
      baseApi.clear();
    }
  }
}
