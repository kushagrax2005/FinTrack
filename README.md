# Hand Sign Language Recognition System

A real-time hand gesture recognition system built using Python, MediaPipe, a temporal LSTM/RNN model, and Flask. The system extracts hand landmark features from video frames and uses a trained deep learning model to classify dynamic hand gestures.

## Features

- **Hand Landmark Detection:** Uses MediaPipe to extract 21 three-dimensional hand landmarks per frame.
- **Feature Extraction:** Processes 63 spatial features per frame.
- **Temporal Gesture Recognition:** Uses an LSTM/RNN model to learn patterns across consecutive frames.
- **Real-Time Prediction:** Processes live video input for gesture recognition.
- **REST API:** Integrates model inference through a Flask backend.

## Tech Stack

- **Language:** Python
- **Computer Vision:** OpenCV, MediaPipe (confirm the libraries used in your implementation)
- **Deep Learning:** TensorFlow/Keras or PyTorch (specify the framework actually used)
- **Backend:** Flask, REST API
- **Tools:** Git, GitHub

## System Workflow

1. Capture video frames from the input camera or video source.
2. Detect the hand and extract its 21 three-dimensional landmarks using MediaPipe.
3. Convert the landmarks into a 63-feature representation for each frame.
4. Pass a sequence of frame features to the trained temporal LSTM/RNN model.
5. Predict the recognized gesture and return the result through the application/API.

## Project Structure

Update this section to match the actual files and folders in your repository.

```text
Hand-Sign-Language-detection/
├── app.py                 # Flask application (if applicable)
├── model/                 # Trained model files (if applicable)
├── requirements.txt       # Python dependencies
├── README.md
└── ...                    # Dataset, training and inference files
```

## Installation and Setup

### 1. Clone the repository

```bash
git clone https://github.com/kushagrax2005/Hand-Sign-Language-detection.git
cd Hand-Sign-Language-detection
```

### 2. Create a virtual environment

```bash
python -m venv .venv
```

Activate it:

- Windows: `.venv\\Scripts\\activate`
- macOS/Linux: `source .venv/bin/activate`

### 3. Install dependencies

```bash
pip install -r requirements.txt
```

Ensure that `requirements.txt` lists the dependencies actually required by your project.

### 4. Run the application

Use the actual entry-point command from your repository. For example, if your Flask entry point is `app.py`:

```bash
python app.py
```

## Model Performance

- **Reported classification accuracy:** 94%+ (include the evaluation dataset, test split, and measurement method when available).
- **Reported inference latency:** Under 45 ms (specify hardware, measurement method, and whether this is model-only or end-to-end latency).

Results depend on the dataset, environment, and evaluation setup.

## Applications

- Real-time hand gesture recognition.
- Gesture-based human-computer interaction.
- Potential accessibility-focused communication interfaces.

## Future Improvements

- Expand the gesture vocabulary and training dataset.
- Evaluate robustness across users, lighting conditions, and camera angles.
- Improve model generalization and inference efficiency.
- Build a polished user interface for live predictions.

## Author

**Kushagra Chaubey**

- GitHub: https://github.com/kushagrax2005

## Acknowledgements

- MediaPipe for hand landmark detection.
- The open-source tools and libraries used in the project.
