/*
 * Copyright (c) 2016 Wertarbyte <http://wertarbyte.com>
 *
 * This file is part of the RenderService.
 *
 * Wertarbyte RenderService is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wertarbyte RenderService is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Wertarbyte RenderService.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.lemaik.chunkymap.rendering.rs;

import com.google.gson.Gson;
import de.lemaik.chunkymap.rendering.RenderException;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import se.llbit.util.TaskTracker;

public class ApiClient {

  private static final Gson gson = new Gson();
  private final String baseUrl;
  private final OkHttpClient client;

  public ApiClient(String baseUrl) {
    this.baseUrl = baseUrl;
    client = new OkHttpClient.Builder().build();
  }

  public CompletableFuture<RenderJob> createJob(byte[] scene, byte[] octree, byte[] skymap,
      String skymapName, String texturepack, int targetSpp, TaskTracker taskTracker) {
    CompletableFuture<RenderJob> result = new CompletableFuture<>();

    MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
        .setType(MediaType.parse("multipart/form-data"))
        .addFormDataPart("scene", "scene.json",
            byteBody(scene, () -> taskTracker.task("Upload scene...")))
        .addFormDataPart("octree", "scene.octree2",
            byteBody(octree, () -> taskTracker.task("Upload octree...")))
        .addFormDataPart("targetSpp", "" + targetSpp)
        .addFormDataPart("transient", "true");

    if (skymap != null) {
      multipartBuilder = multipartBuilder.addFormDataPart("skymap", skymapName,
          byteBody(skymap, () -> taskTracker.task("Upload skymap...")));
    }

    if (texturepack != null) {
      multipartBuilder = multipartBuilder.addFormDataPart("texturepack", texturepack);
    }

    client.newCall(new Request.Builder()
        .url(baseUrl + "/jobs")
        .post(multipartBuilder.build())
        .build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) throws IOException {
            if (response.code() == 201) {
              try (InputStreamReader reader = new InputStreamReader(response.body().byteStream())) {
                result.complete(gson.fromJson(reader, RenderJob.class));
              } catch (IOException e) {
                result.completeExceptionally(e);
              }
            } else {
              result.completeExceptionally(new IOException("The render job could not be created"));
            }
          }
        });

    return result;
  }

  public CompletableFuture<RenderJob> createJob(byte[] scene, List<File> regionFiles, byte[] skymap,
      String skymapName, String texturepack, int targetSpp, TaskTracker taskTracker) {
    CompletableFuture<RenderJob> result = new CompletableFuture<>();

    MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
        .setType(MediaType.parse("multipart/form-data"))
        .addFormDataPart("scene", "scene.json",
            byteBody(scene, () -> taskTracker.task("Upload scene...")))
        .addFormDataPart("targetSpp", "" + targetSpp)
        .addFormDataPart("transient", "true");

    for (File region : regionFiles) {
      multipartBuilder = multipartBuilder.addFormDataPart("region", region.getName(),
          fileBody(region, () -> taskTracker.task("Upload region " + region.getName())));
    }

    if (skymap != null) {
      multipartBuilder = multipartBuilder.addFormDataPart("skymap", skymapName,
          byteBody(skymap, () -> taskTracker.task("Upload skymap...")));
    }

    if (texturepack != null) {
      multipartBuilder = multipartBuilder.addFormDataPart("texturepack", texturepack);
    }

    client.newCall(new Request.Builder()
        .url(baseUrl + "/jobs")
        .post(multipartBuilder.build())
        .build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) throws IOException {
            if (response.code() == 201) {
              try (InputStreamReader reader = new InputStreamReader(response.body().byteStream())) {
                result.complete(gson.fromJson(reader, RenderJob.class));
              } catch (IOException e) {
                result.completeExceptionally(e);
              }
            } else {
              result.completeExceptionally(
                  new IOException("The render job could not be created: " + response.message()));
            }
          }
        });

    return result;
  }

  public CompletableFuture<RenderJob> createJob(File scene, File octree, File grass, File foliage,
      File skymap, String texturepack, TaskTracker taskTracker) throws IOException {
    CompletableFuture<RenderJob> result = new CompletableFuture<>();

    MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
        .setType(MediaType.parse("multipart/form-data"))
        .addFormDataPart("scene", "scene.json",
            fileBody(scene, () -> taskTracker.task("Upload scene...")))
        .addFormDataPart("octree", "scene.octree",
            fileBody(octree, () -> taskTracker.task("Upload octree...")))
        .addFormDataPart("targetSpp", "100")
        .addFormDataPart("transient", "true");

    if (skymap != null) {
      multipartBuilder = multipartBuilder.addFormDataPart("skymap", skymap.getName(),
          fileBody(skymap, () -> taskTracker.task("Upload skymap...")));
    }

    if (texturepack != null) {
      multipartBuilder = multipartBuilder.addFormDataPart("texturepack", texturepack);
    }

    client.newCall(new Request.Builder()
        .url(baseUrl + "/jobs")
        .post(multipartBuilder.build())
        .build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) throws IOException {
            if (response.code() == 201) {
              try (InputStreamReader reader = new InputStreamReader(response.body().byteStream())) {
                result.complete(gson.fromJson(reader, RenderJob.class));
              } catch (IOException e) {
                result.completeExceptionally(e);
              }
            } else {
              result.completeExceptionally(new IOException("The render job could not be created"));
            }
          }
        });

    return result;
  }

  public CompletableFuture<RenderJob> waitForCompletion(RenderJob renderJob, long timeout,
      TimeUnit unit) {
    if (renderJob.getSpp() >= renderJob.getTargetSpp()) {
      // job is already completed
      return CompletableFuture.completedFuture(renderJob);
    }

    long then = System.currentTimeMillis();
    CompletableFuture<RenderJob> completedJob = new CompletableFuture<>();
    new Thread(() -> {
      RenderJob current = renderJob;
      try {
        while (current.getSpp() < current.getTargetSpp()) {
          if (then + unit.toMillis(timeout) < System.currentTimeMillis()) {
            completedJob
                .completeExceptionally(
                    new RenderException("Timeout after " + unit.toMillis(timeout) + " ms"));
            return;
          }
          Thread.sleep(500);
          current = getJob(current.getId()).get();
        }
        completedJob.complete(current);
      } catch (Exception e) {
        completedJob.completeExceptionally(e);
      }
    }).start();
    return completedJob;
  }

  public CompletableFuture<RenderJob> getJob(String jobId) {
    CompletableFuture<RenderJob> result = new CompletableFuture<>();

    client.newCall(new Request.Builder()
        .url(baseUrl + "/jobs/" + jobId).get().build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) {
            if (response.code() == 200) {
              try (InputStreamReader reader = new InputStreamReader(response.body().byteStream())) {
                result.complete(gson.fromJson(reader, RenderJob.class));
              } catch (IOException e) {
                result.completeExceptionally(e);
              }
            } else {
              result.completeExceptionally(new IOException("The job could not be downloaded"));
            }
          }
        });

    return result;
  }

  private static RequestBody fileBody(final File file, Supplier<TaskTracker.Task> taskCreator) {
    TaskTracker.Task task = taskCreator.get();
    return new RequestBody() {
      @Override
      public MediaType contentType() {
        return MediaType.parse("application/octet-stream");
      }

      @Override
      public long contentLength() {
        return file.length();
      }

      @Override
      public void writeTo(BufferedSink bufferedSink) throws IOException {
        try (Source source = Okio.source(file)) {
          long read = 0;
          for (long readCount; (readCount = source.read(bufferedSink.buffer(), 2048)) != -1; ) {
            read += readCount;
            bufferedSink.flush();
            task.update((int) contentLength(), (int) read);
          }
        }
        task.close();
      }
    };
  }

  private static RequestBody byteBody(final byte[] content,
      Supplier<TaskTracker.Task> taskCreator) {
    TaskTracker.Task task = taskCreator.get();
    return new RequestBody() {
      @Override
      public MediaType contentType() {
        return MediaType.parse("application/octet-stream");
      }

      @Override
      public long contentLength() {
        return content.length;
      }

      @Override
      public void writeTo(BufferedSink bufferedSink) throws IOException {
        bufferedSink.write(content);
        task.close();
      }
    };
  }

  public BufferedImage getPicture(String id) throws IOException {
    BufferedImage image = ImageIO.read(new URL(baseUrl + "/jobs/" + id + "/latest.png"));
    BufferedImage img = new BufferedImage(image.getWidth(), image.getHeight(),
        BufferedImage.TYPE_INT_ARGB);
    Graphics g = img.getGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    return img;
  }
}
