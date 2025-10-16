package unc.edu.pe.appcorteideal;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
// ... otros imports

import java.io.IOException;

import unc.edu.pe.appcorteideal.databinding.ActivityMainBinding;


public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher; // NUEVO lanzador para la galería
    private FaceDetector faceDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 1. Configurar el detector de rostros de ML Kit
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build();
        faceDetector = FaceDetection.getClient(options);

        // 2. Configurar lanzador para la CÁMARA
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        if (extras != null) {
                            Bitmap imageBitmap = (Bitmap) extras.get("data");
                            procesarBitmap(imageBitmap);
                        }
                    } else {
                        Toast.makeText(this, "Captura de cámara cancelada", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // 3. --- NUEVO --- Configurar lanzador para la GALERÍA
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            // Convertimos la URI de la imagen en un Bitmap
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                            procesarBitmap(bitmap);
                        } catch (IOException e) {
                            Log.e("Gallery", "Error al cargar la imagen de la galería", e);
                            Toast.makeText(this, "No se pudo cargar la imagen.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Selección de galería cancelada", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // 4. Configurar lanzador para PERMISOS
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openCamera();
                    } else {
                        Toast.makeText(this, "Permiso de cámara denegado.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        // 5. Configurar listeners de los botones
        binding.btnTomarFoto.setOnClickListener(v -> handleCameraPermission());

        binding.btnCargarFoto.setOnClickListener(v -> {
            // NUEVO: Abrir la galería
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(galleryIntent);
        });
    }

    /**
     * NUEVO: Centraliza el procesamiento del bitmap para reutilizar código
     */
    private void procesarBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            binding.ivFotoRostro.setImageBitmap(bitmap);
            detectarFormaRostro(bitmap);
        }
    }

    private void handleCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(cameraIntent);
    }

    // ----- El resto de tu código (detectarFormaRostro, etc.) no necesita cambios -----
    private void detectarFormaRostro(Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(this, "No se proporcionó una imagen.", Toast.LENGTH_SHORT).show();
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        binding.tvFormaRostro.setText("No se detectó rostro");
                        binding.tvRecomendacion.setText("-");
                        return;
                    }
                    Face face = faces.get(0);
                    String forma = clasificarFormaRostro(face);
                    String recomendaciones = recomendarCortes(forma);

                    binding.tvFormaRostro.setText(forma);
                    binding.tvRecomendacion.setText(recomendaciones);
                })
                .addOnFailureListener(e -> {
                    Log.e("MLKit", "Error en la detección de rostro", e);
                    Toast.makeText(MainActivity.this, "Error al procesar la imagen", Toast.LENGTH_SHORT).show();
                });
    }

    private String clasificarFormaRostro(Face face) {
        float width = face.getBoundingBox().width();
        float height = face.getBoundingBox().height();
        float ratio = height / width;

        Log.d("FaceShape", "Ancho: " + width + ", Alto: " + height + ", Ratio: " + ratio);

        if (ratio > 1.25) {
            return "Ovalado";
        } else if (ratio > 1.0 && ratio <= 1.25) {
            return "Cuadrado";
        } else if (ratio <= 1.0) {
            return "Redondo";
        }
        return "Desconocida";
    }

    private String recomendarCortes(String formaRostro) {
        switch (formaRostro) {
            case "Ovalado":
                return "• Casi todos los estilos te quedan bien.\n• Bob largo o corto.\n• Flequillos rectos o de lado.\n• Pelo largo con capas.";
            case "Cuadrado":
                return "• Cortes con capas suaves para redondear ángulos.\n• Pelo largo y ondulado.\n• Flequillos de lado.\n• Evita los cortes rectos a la altura de la mandíbula.";
            case "Redondo":
                return "• Cortes que añadan altura y volumen en la parte superior.\n• Estilo Pixie con volumen arriba.\n• Pelo largo y liso.\n• Evita los bobs cortos y flequillos rectos.";
            default:
                return "No hay recomendaciones disponibles.";
        }
    }
}
