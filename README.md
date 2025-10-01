## Media 3 Transformer Demo

In the latest commit, we want to demonstrate the discrepancy issue that we encountered when we create a video with a generated `Bitmap`.
The discrepancy happen between the usage of single `BitmapOverlay` where we combine all the `Bitmap`s first and the usage of multiple `BitmapOverlay`s where `Transformer` combine all the `Bitmap`s.

## Usage

Press the `export` button in the app, it will generate two videos in `/Download/` directory:

1. `merged-output*.mp4` --> This is the video created with single `BitmapOverlay`
2. `separated-output*.mp4` --> This is the video create with multiple `BitmapOverlay`

<img width="200" alt="first_step" src="https://github.com/user-attachments/assets/9f4169e0-7a50-4302-b970-7755a23d7cbf" />
<img width="200" alt="second_step" src="https://github.com/user-attachments/assets/9026994b-4c3d-4357-a391-d59904796ecb" />

## Issue

Here you can see the difference between the image when we're using single `BitmapOverlay` vs multiple `BitmapOverlay`.
As a context, the result that we are expected is the one in single `BitmapOverlay`

| BitmapOverlay | Result |
| --- | --- |
| Single | <img width="200" alt="CleanShot 2025-10-01 at 09 13 05@2x" src="https://github.com/user-attachments/assets/29be3720-6136-498c-aaf3-3d55b95a28a7" /> |
| Multiple | <img width="200" alt="CleanShot 2025-10-01 at 09 13 15@2x" src="https://github.com/user-attachments/assets/a2e56a0b-825e-43b6-aa50-e19e405b4c73" /> |
