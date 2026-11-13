package kr.co.badara.lightsign;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public final class UpdateFile extends ContentProvider {
    static final Uri URI=Uri.parse("content://kr.co.badara.lightsign.update/light-sign.apk");
    static File file(Context context){return new File(context.getCacheDir(),"light-sign-update.apk");}
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return URI.equals(uri)?"application/vnd.android.package-archive":null;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!URI.equals(uri)||!"r".equals(mode))throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(getContext()),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        if(!URI.equals(uri))return null;
        if(projection==null)projection=new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
        MatrixCursor cursor=new MatrixCursor(projection);
        Object[] row=new Object[projection.length];
        for(int i=0;i<row.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(projection[i]))row[i]="light-sign.apk";
            if(OpenableColumns.SIZE.equals(projection[i]))row[i]=file(getContext()).length();
        }
        cursor.addRow(row);return cursor;
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues v,String s,String[] args){throw new UnsupportedOperationException();}
}
