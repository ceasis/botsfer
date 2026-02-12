(function () {
  const root = document.getElementById('root');
  const ballContainer = document.getElementById('ball-container');
  const panel = document.getElementById('panel');
  const messagesEl = document.getElementById('messages');
  const inputEl = document.getElementById('input');
  const sendBtn = document.getElementById('send-btn');
  const voiceBtn = document.getElementById('voice-btn');
  const voiceToggleBtn = document.getElementById('voice-toggle-btn');
  const voiceToggleLabel = document.getElementById('voice-toggle-label');
  const voiceStatus = document.getElementById('voice-status');
  const closeBtn = document.getElementById('close-btn');

  function focusInput() {
    if (!inputEl || typeof inputEl.focus !== 'function') return;
    try {
      inputEl.focus({ preventScroll: true });
    } catch (err) {
      inputEl.focus();
    }
  }

  function focusInputSoon() {
    setTimeout(function () {
      if (!root.classList.contains('expanded')) return;
      focusInput();
    }, 60);
  }

  function ensureInputFocus() {
    let tries = 0;
    const timer = setInterval(function () {
      tries += 1;
      if (!root.classList.contains('expanded')) {
        clearInterval(timer);
        return;
      }
      if (document.activeElement === inputEl) {
        clearInterval(timer);
        return;
      }
      focusInput();
      if (tries >= 8) {
        clearInterval(timer);
      }
    }, 50);
  }

  function expand() {
    root.classList.add('expanded');
    if (typeof window.java !== 'undefined' && window.java.expand) {
      window.java.expand();
    }
    focusInputSoon();
    ensureInputFocus();
  }

  function collapse() {
    root.classList.remove('expanded');
    if (typeof window.java !== 'undefined' && window.java.collapse) {
      window.java.collapse();
    }
  }

  function togglePanel() {
    if (root.classList.contains('expanded')) {
      collapse();
    } else {
      expand();
    }
  }
  // Block native HTML5 drag-and-drop so no ghost "Copy" appears when dragging the ball
  ballContainer.setAttribute('draggable', 'false');
  var ball = ballContainer.querySelector('.ball');
  if (ball) ball.setAttribute('draggable', 'false');
  function noDrag(e) {
    e.preventDefault();
    e.stopPropagation();
    return false;
  }
  ballContainer.addEventListener('dragstart', noDrag, true);
  ballContainer.addEventListener('dragend', noDrag, true);
  ballContainer.addEventListener('drag', noDrag, true);
  root.addEventListener('dragstart', noDrag, true);
  document.addEventListener('dragover', noDrag, true);
  document.addEventListener('drop', noDrag, true);
  document.addEventListener('dragenter', noDrag, true);
  document.addEventListener('dragleave', noDrag, true);

  // Ball click/dblclick are handled by Java event filters which forward
  // clicks to JS via document.elementFromPoint(). Drag is handled in Java.

  closeBtn.addEventListener('click', collapse);

  inputEl.addEventListener('mousedown', function () {
    focusInput();
  });

  document.addEventListener('keydown', function (e) {
    if (!root.classList.contains('expanded')) return;
    if (document.activeElement === inputEl) return;
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.key && e.key.length === 1) {
      focusInput();
    }
  }, true);

  function appendMessage(text, isUser) {
    const div = document.createElement('div');
    div.className = 'message ' + (isUser ? 'user' : 'bot');
    div.textContent = text;
    messagesEl.appendChild(div);
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function setVoiceStatus(text, show) {
    voiceStatus.textContent = text || '';
    voiceStatus.hidden = !show;
  }

  async function sendMessage(text) {
    if (!text || !text.trim()) return;
    const msg = text.trim();
    inputEl.value = '';
    appendMessage(msg, true);

    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: msg })
      });
      const data = await res.json();
      appendMessage(data.reply || 'No reply.', false);
    } catch (e) {
      appendMessage('Could not reach server. Check that the app is running.', false);
    }
  }

  sendBtn.addEventListener('click', function () {
    sendMessage(inputEl.value);
    focusInputSoon();
  });

  inputEl.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage(inputEl.value);
      focusInputSoon();
    }
  });

  // Fallback for embedded WebView cases where input keydown can be flaky.
  document.addEventListener('keydown', function (e) {
    if (!root.classList.contains('expanded')) return;
    if (document.activeElement !== inputEl) return;
    if (e.key !== 'Enter' || e.shiftKey) return;
    e.preventDefault();
    sendMessage(inputEl.value);
    focusInputSoon();
  }, true);

  // Voice (Web Speech API)
  let recognition = null;
  let isListening = false;
  let voiceEnabled = false;
  let nativeVoicePollTimer = null;

  function hasNativeVoice() {
    return typeof window.java !== 'undefined'
      && typeof window.java.isNativeVoiceAvailable === 'function'
      && window.java.isNativeVoiceAvailable();
  }

  function setListeningUi(listening) {
    isListening = !!listening;
    voiceBtn.classList.toggle('listening', isListening);
    if (isListening) {
      setVoiceStatus('Listening...', true);
    } else if (!voiceEnabled) {
      setVoiceStatus('', false);
    }
  }

  function startNativeVoicePolling() {
    if (nativeVoicePollTimer) {
      clearInterval(nativeVoicePollTimer);
      nativeVoicePollTimer = null;
    }
    nativeVoicePollTimer = setInterval(function () {
      if (typeof window.java === 'undefined') return;
      var err = window.java.consumeNativeVoiceError();
      if (err) {
        setListeningUi(false);
        setVoiceStatus(err, true);
        clearInterval(nativeVoicePollTimer);
        nativeVoicePollTimer = null;
        return;
      }

      var text = window.java.consumeNativeVoiceTranscript();
      if (text) {
        appendMessage(text, false);
        setListeningUi(false);
        setVoiceStatus('', false);
        clearInterval(nativeVoicePollTimer);
        nativeVoicePollTimer = null;
        return;
      }

      if (!window.java.isNativeVoiceListening()) {
        setListeningUi(false);
        clearInterval(nativeVoicePollTimer);
        nativeVoicePollTimer = null;
      }
    }, 180);
  }

  function setVoiceEnabled(enabled) {
    voiceEnabled = !!enabled;
    if (!voiceEnabled && isListening) {
      if (hasNativeVoice() && typeof window.java.stopNativeVoice === 'function') {
        window.java.stopNativeVoice();
      } else if (recognition) {
        recognition.stop();
      }
      setListeningUi(false);
    }
    if (voiceToggleLabel) {
      voiceToggleLabel.textContent = voiceEnabled ? 'Voice On' : 'Voice Off';
    }
    if (voiceToggleBtn) {
      voiceToggleBtn.classList.toggle('on', voiceEnabled);
      voiceToggleBtn.title = voiceEnabled ? 'Turn voice off' : 'Turn voice on';
    }
    voiceBtn.classList.toggle('off', !voiceEnabled);
    voiceBtn.title = voiceEnabled ? 'Start voice input' : 'Voice is off';
    voiceBtn.setAttribute('aria-label', voiceEnabled ? 'Start voice input' : 'Voice is off');
    if (!voiceEnabled) {
      setVoiceStatus('', false);
    }
  }

  if (!hasNativeVoice() && ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window)) {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.interimResults = true;
    recognition.lang = 'en-US';

    recognition.onresult = function (e) {
      const last = e.results.length - 1;
      const transcript = e.results[last][0].transcript;
      if (e.results[last].isFinal) {
        inputEl.value = transcript;
        setVoiceStatus('', false);
      } else {
        setVoiceStatus('Listening: ' + transcript, true);
      }
    };

    recognition.onend = function () {
      setListeningUi(false);
      setVoiceStatus('', false);
    };

    recognition.onerror = function () {
      setListeningUi(false);
      setVoiceStatus('Voice error. Try again.', true);
    };
  }

  if (voiceToggleBtn) {
    voiceToggleBtn.addEventListener('click', function () {
      if (!recognition && !hasNativeVoice()) {
        setVoiceStatus('Voice not supported in this browser.', true);
        return;
      }
      setVoiceEnabled(!voiceEnabled);
      if (voiceEnabled) {
        setVoiceStatus('Voice enabled.', true);
        setTimeout(function () { setVoiceStatus('', false); }, 1200);
      }
    });
  }

  voiceBtn.addEventListener('click', function () {
    if (!recognition && !hasNativeVoice()) {
      setVoiceStatus('Voice not supported in this browser.', true);
      return;
    }
    if (!voiceEnabled) {
      setVoiceEnabled(true);
      setVoiceStatus('Voice enabled.', true);
      setTimeout(function () { setVoiceStatus('', false); }, 900);
    }
    if (isListening) {
      if (hasNativeVoice() && typeof window.java.stopNativeVoice === 'function') {
        window.java.stopNativeVoice();
      } else if (recognition) {
        recognition.stop();
      }
      setListeningUi(false);
      return;
    }

    setListeningUi(true);
    if (hasNativeVoice()) {
      if (!window.java.startNativeVoice()) {
        setListeningUi(false);
        setVoiceStatus('Voice recognizer is busy.', true);
        return;
      }
      startNativeVoicePolling();
      return;
    }

    recognition.start();
  });

  setVoiceEnabled(false);

  // Initial greeting
  appendMessage('Hi! Type a message or use the microphone.', false);
})();
