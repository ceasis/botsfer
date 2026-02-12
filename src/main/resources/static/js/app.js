(function () {
  const root = document.getElementById('root');
  const ballContainer = document.getElementById('ball-container');
  const messagesEl = document.getElementById('messages');
  const inputEl = document.getElementById('input');
  const sendBtn = document.getElementById('send-btn');
  const voiceBtn = document.getElementById('voice-btn');
  const voiceStatus = document.getElementById('voice-status');
  const closeBtn = document.getElementById('close-btn');
  const clearBtn = document.getElementById('clear-btn');

  // ═══ Window expand/collapse ═══

  function focusInput() {
    if (!inputEl || typeof inputEl.focus !== 'function') return;
    try { inputEl.focus({ preventScroll: true }); } catch (err) { inputEl.focus(); }
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
      if (!root.classList.contains('expanded')) { clearInterval(timer); return; }
      if (document.activeElement === inputEl) { clearInterval(timer); return; }
      focusInput();
      if (tries >= 8) clearInterval(timer);
    }, 50);
  }

  function expand() {
    root.classList.add('expanded');
    if (typeof window.java !== 'undefined' && window.java.expand) window.java.expand();
    focusInputSoon();
    ensureInputFocus();
  }

  function collapse() {
    root.classList.remove('expanded');
    if (typeof window.java !== 'undefined' && window.java.collapse) window.java.collapse();
  }

  // ═══ Anti-drag (prevent "Copy" ghost on Windows) ═══

  ballContainer.setAttribute('draggable', 'false');
  var ball = ballContainer.querySelector('.ball');
  if (ball) ball.setAttribute('draggable', 'false');
  function noDrag(e) { e.preventDefault(); e.stopPropagation(); return false; }
  ballContainer.addEventListener('dragstart', noDrag, true);
  ballContainer.addEventListener('dragend', noDrag, true);
  ballContainer.addEventListener('drag', noDrag, true);
  root.addEventListener('dragstart', noDrag, true);
  document.addEventListener('dragover', noDrag, true);
  document.addEventListener('drop', noDrag, true);
  document.addEventListener('dragenter', noDrag, true);
  document.addEventListener('dragleave', noDrag, true);

  closeBtn.addEventListener('click', collapse);

  // Clear chat
  if (clearBtn) {
    clearBtn.addEventListener('click', function () {
      messagesEl.innerHTML = '';
      appendMessage('Chat cleared. How can I help?', false);
    });
  }

  inputEl.addEventListener('mousedown', function () { focusInput(); });

  // Auto-focus input when typing
  document.addEventListener('keydown', function (e) {
    if (!root.classList.contains('expanded')) return;
    if (document.activeElement === inputEl) return;
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.key && e.key.length === 1) focusInput();
  }, true);

  // ═══ Message rendering ═══

  function getTimeStr() {
    var d = new Date();
    var h = d.getHours();
    var m = d.getMinutes();
    return (h < 10 ? '0' : '') + h + ':' + (m < 10 ? '0' : '') + m;
  }

  function appendMessage(text, isUser) {
    var wrapper = document.createElement('div');
    wrapper.className = 'message-wrapper ' + (isUser ? 'user' : 'bot');

    var msg = document.createElement('div');
    msg.className = 'message ' + (isUser ? 'user' : 'bot');
    msg.textContent = text;

    var time = document.createElement('div');
    time.className = 'message-time';
    time.textContent = getTimeStr();

    wrapper.appendChild(msg);
    wrapper.appendChild(time);
    messagesEl.appendChild(wrapper);
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function showThinking() {
    var el = document.createElement('div');
    el.className = 'thinking';
    el.id = 'thinking-indicator';
    for (var i = 0; i < 3; i++) {
      var dot = document.createElement('div');
      dot.className = 'thinking-dot';
      el.appendChild(dot);
    }
    messagesEl.appendChild(el);
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function hideThinking() {
    var el = document.getElementById('thinking-indicator');
    if (el) el.remove();
  }

  function appendStatus(text) {
    var el = document.createElement('div');
    el.className = 'message-status';
    el.textContent = text;
    messagesEl.appendChild(el);
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function clearStatusMessages() {
    var items = messagesEl.querySelectorAll('.message-status');
    for (var i = 0; i < items.length; i++) items[i].remove();
  }

  function setVoiceStatus(text, show) {
    voiceStatus.textContent = text || '';
    voiceStatus.hidden = !show;
  }

  // ═══ Send message ═══

  let sendingMessage = false;
  let statusPollTimer = null;

  function startStatusPolling() {
    if (statusPollTimer) return;
    statusPollTimer = setInterval(async function () {
      try {
        var res = await fetch('/api/chat/status');
        var data = await res.json();
        if (data.messages && data.messages.length > 0) {
          hideThinking();
          for (var i = 0; i < data.messages.length; i++) {
            appendStatus(data.messages[i]);
          }
        }
      } catch (e) { /* ignore */ }
    }, 500);
  }

  function stopStatusPolling() {
    if (statusPollTimer) {
      clearInterval(statusPollTimer);
      statusPollTimer = null;
    }
  }

  async function sendMessage(text) {
    if (!text || !text.trim()) return;
    if (sendingMessage) return;
    sendingMessage = true;
    const msg = text.trim();
    inputEl.value = '';
    appendMessage(msg, true);
    showThinking();
    startStatusPolling();

    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: msg })
      });
      const data = await res.json();
      stopStatusPolling();
      clearStatusMessages();
      hideThinking();
      appendMessage(data.reply || 'No reply.', false);
    } catch (e) {
      stopStatusPolling();
      clearStatusMessages();
      hideThinking();
      appendMessage('Could not reach server.', false);
    } finally {
      sendingMessage = false;
    }
  }

  sendBtn.addEventListener('click', function () {
    sendMessage(inputEl.value);
    focusInputSoon();
  });

  // Enter key — multiple fallbacks for JavaFX WebView
  inputEl.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage(inputEl.value);
      focusInputSoon();
    }
  });

  document.addEventListener('keydown', function (e) {
    if (!root.classList.contains('expanded')) return;
    if (document.activeElement !== inputEl) return;
    if (e.key !== 'Enter' || e.shiftKey) return;
    e.preventDefault();
    sendMessage(inputEl.value);
    focusInputSoon();
  }, true);

  inputEl.addEventListener('keyup', function (e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (inputEl.value && inputEl.value.trim()) {
        sendMessage(inputEl.value);
        focusInputSoon();
      }
    }
  });

  document.addEventListener('keyup', function (e) {
    if (!root.classList.contains('expanded')) return;
    if (e.keyCode !== 13 || e.shiftKey) return;
    if (inputEl.value && inputEl.value.trim()) {
      sendMessage(inputEl.value);
      focusInputSoon();
    }
  }, true);

  // ═══ Voice ═══

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
    if (nativeVoicePollTimer) { clearInterval(nativeVoicePollTimer); nativeVoicePollTimer = null; }
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
        var prefix = '__AUDIO_RESULT__';
        if (text.indexOf(prefix) === 0) {
          try {
            var payload = JSON.parse(text.substring(prefix.length));
            if (payload.transcript) appendMessage(payload.transcript, true);
            if (payload.reply) appendMessage(payload.reply, false);
          } catch (parseErr) {
            appendMessage(text, false);
          }
        } else {
          appendMessage(text, false);
        }
        clearInterval(nativeVoicePollTimer);
        nativeVoicePollTimer = null;
        if (voiceEnabled) {
          setTimeout(function () {
            if (!voiceEnabled) return;
            setListeningUi(true);
            if (window.java.startNativeVoice()) { startNativeVoicePolling(); }
            else { setListeningUi(false); }
          }, 300);
        } else {
          setListeningUi(false);
          setVoiceStatus('', false);
        }
        return;
      }

      if (!window.java.isNativeVoiceListening()) {
        clearInterval(nativeVoicePollTimer);
        nativeVoicePollTimer = null;
        if (voiceEnabled) {
          setTimeout(function () {
            if (!voiceEnabled) return;
            setListeningUi(true);
            if (window.java.startNativeVoice()) { startNativeVoicePolling(); }
            else { setListeningUi(false); }
          }, 300);
        } else {
          setListeningUi(false);
        }
      }
    }, 180);
  }

  function startListening() {
    if (isListening) return;
    setListeningUi(true);
    if (hasNativeVoice()) {
      if (window.java.startNativeVoice()) { startNativeVoicePolling(); }
      else { setListeningUi(false); }
      return;
    }
    if (recognition) {
      try { recognition.start(); } catch (e) { /* already started */ }
    }
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
    voiceBtn.classList.toggle('off', !voiceEnabled);
    voiceBtn.title = voiceEnabled ? 'Listening' : 'Voice is off';
    voiceBtn.setAttribute('aria-label', voiceEnabled ? 'Listening' : 'Voice is off');
    if (!voiceEnabled) {
      setVoiceStatus('', false);
    }
    if (voiceEnabled && !isListening) {
      setTimeout(startListening, 200);
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
      if (voiceEnabled) {
        setTimeout(function () {
          if (!voiceEnabled) return;
          setListeningUi(true);
          try { recognition.start(); } catch (e) { /* already started */ }
        }, 300);
      } else {
        setListeningUi(false);
        setVoiceStatus('', false);
      }
    };

    recognition.onerror = function (ev) {
      if (voiceEnabled && ev.error !== 'not-allowed' && ev.error !== 'service-not-allowed') {
        setTimeout(function () {
          if (!voiceEnabled) return;
          setListeningUi(true);
          try { recognition.start(); } catch (e) { /* already started */ }
        }, 500);
      } else {
        setListeningUi(false);
        setVoiceStatus('Voice error. Try again.', true);
      }
    };
  }

  voiceBtn.addEventListener('click', function () {
    if (!recognition && !hasNativeVoice()) {
      setVoiceStatus('Voice not supported.', true);
      return;
    }
    setVoiceEnabled(!voiceEnabled);
  });

  // Don't auto-start voice — user clicks mic to enable

  // ═══ Async agent results polling ═══

  setInterval(async function () {
    try {
      var res = await fetch('/api/chat/async');
      var data = await res.json();
      if (data.hasResult && data.reply) {
        appendMessage(data.reply, false);
      }
    } catch (e) { /* ignore */ }
  }, 2000);

  // ═══ Greeting ═══

  appendMessage('Hi! I\'m Botsfer. Ask me anything or give me a command.', false);
})();
