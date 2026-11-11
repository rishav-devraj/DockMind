import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import * as ImagePicker from 'expo-image-picker';

const MAX_LONG_EDGE = 1600;
const JPEG_QUALITY = 0.7;

export type PickResult =
  | { kind: 'picked'; asset: ImagePicker.ImagePickerAsset }
  | { kind: 'cancelled' }
  | { kind: 'denied'; message: string };

export async function takePhoto(): Promise<PickResult> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) {
    return {
      kind: 'denied',
      message: 'Camera access is off. Turn it on in Settings, or choose a photo from the gallery.',
    };
  }
  return toPickResult(await ImagePicker.launchCameraAsync({ mediaTypes: 'images', quality: 1 }));
}

export async function pickFromGallery(): Promise<PickResult> {
  return toPickResult(
    await ImagePicker.launchImageLibraryAsync({ mediaTypes: 'images', quality: 1 }),
  );
}

function toPickResult(result: ImagePicker.ImagePickerResult): PickResult {
  if (result.canceled || !result.assets[0]) return { kind: 'cancelled' };
  return { kind: 'picked', asset: result.assets[0] };
}

/** Shrinks to about 1600 px on the long edge and returns the URI of a compressed JPEG. */
export async function downscale(asset: { uri: string; width: number; height: number }) {
  const context = ImageManipulator.manipulate(asset.uri);
  if (Math.max(asset.width, asset.height) > MAX_LONG_EDGE) {
    context.resize(
      asset.width >= asset.height ? { width: MAX_LONG_EDGE } : { height: MAX_LONG_EDGE },
    );
  }
  const rendered = await context.renderAsync();
  const saved = await rendered.saveAsync({ format: SaveFormat.JPEG, compress: JPEG_QUALITY });
  return saved.uri;
}
