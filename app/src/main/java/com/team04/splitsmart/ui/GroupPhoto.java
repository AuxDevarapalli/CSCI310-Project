package com.team04.splitsmart.ui;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.widget.ImageView;

import com.team04.splitsmart.model.Group;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Converts the picked image to a bounded thumbnail before it is shared with group members. */
final class GroupPhoto {
    private static final int SIZE = 192;
    private static final int DECODE_LIMIT = 768;

    private GroupPhoto() {
    }

    static byte[] read(ContentResolver resolver, Uri uri) throws IOException {
        Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= 28) {
            bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri),
                    (decoder, info, source) -> {
                        int width = info.getSize().getWidth();
                        int height = info.getSize().getHeight();
                        float scale = Math.min(1f, DECODE_LIMIT / (float) Math.max(width, height));
                        decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                                Math.max(1, Math.round(height * scale)));
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
        } else {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream input = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, options);
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize(options.outWidth, options.outHeight);
            try (InputStream input = resolver.openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
        }
        if (bitmap == null) {
            throw new IOException("Image could not be decoded");
        }
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap square = Bitmap.createBitmap(bitmap, (bitmap.getWidth() - side) / 2,
                (bitmap.getHeight() - side) / 2, side, side);
        Bitmap thumbnail = Bitmap.createScaledBitmap(square, SIZE, SIZE, true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int quality = 88; quality >= 28; quality -= 15) {
            output.reset();
            if (thumbnail.compress(Bitmap.CompressFormat.JPEG, quality, output)
                    && output.size() <= Group.MAX_PHOTO_BYTES) {
                return output.toByteArray();
            }
        }
        throw new IOException("Image could not be compressed");
    }

    static Bitmap decode(byte[] photo) {
        if (photo == null || photo.length == 0 || photo.length > Group.MAX_PHOTO_BYTES) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(photo, 0, photo.length, options);
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize(options.outWidth, options.outHeight);
        return BitmapFactory.decodeByteArray(photo, 0, photo.length, options);
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (Math.max(width, height) / sample > DECODE_LIMIT) {
            sample *= 2;
        }
        return sample;
    }

    static void bind(ImageView view, byte[] photo) {
        Bitmap bitmap = decode(photo);
        view.setImageBitmap(bitmap);
        UiUtils.setVisible(view, bitmap != null);
    }
}
