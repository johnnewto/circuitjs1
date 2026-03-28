    var markdownEditorCm = null;
    function initMarkdownEditor() {
      if (!editorEnabled || markdownEditorCm) return;
      const editor = document.getElementById('markdownEditor');
      if (!editor || typeof CodeMirror === 'undefined') return;
      markdownEditorCm = CodeMirror.fromTextArea(editor, {
        mode: 'markdown',
        lineNumbers: true,
        lineWrapping: true,
        viewportMargin: Infinity
      });
      markdownEditorCm.setSize('100%', '100%');
      markdownEditorCm.setValue(sourceMarkdown || '');
    }
    function ensureEditorAvailable() {
      if (!editorEnabled) return false;
      initMarkdownEditor();
      if (markdownEditorCm) return true;
      return !!document.getElementById('markdownEditor');
    }
    function getEditorText() {
      if (markdownEditorCm) return markdownEditorCm.getValue() || '';
      const editor = document.getElementById('markdownEditor');
      return editor ? (editor.value || '') : '';
    }
    function captureEditorViewState() {
      if (markdownEditorCm) {
        const scroll = markdownEditorCm.getScrollInfo();
        const selections = (typeof markdownEditorCm.listSelections === 'function')
          ? markdownEditorCm.listSelections()
          : null;
        return {
          kind: 'cm',
          left: scroll ? scroll.left : 0,
          top: scroll ? scroll.top : 0,
          selections: selections
        };
      }
      const editor = document.getElementById('markdownEditor');
      if (!editor) return null;
      return {
        kind: 'ta',
        left: editor.scrollLeft || 0,
        top: editor.scrollTop || 0,
        start: (typeof editor.selectionStart === 'number') ? editor.selectionStart : null,
        end: (typeof editor.selectionEnd === 'number') ? editor.selectionEnd : null
      };
    }

    function restoreEditorViewState(state) {
      if (!state) return;
      if (state.kind === 'cm' && markdownEditorCm) {
        markdownEditorCm.operation(function() {
          if (state.selections && typeof markdownEditorCm.setSelections === 'function') {
            markdownEditorCm.setSelections(state.selections);
          }
          markdownEditorCm.scrollTo(state.left || 0, state.top || 0);
        });
        return;
      }
      if (state.kind === 'ta') {
        const editor = document.getElementById('markdownEditor');
        if (!editor) return;
        editor.scrollLeft = state.left || 0;
        editor.scrollTop = state.top || 0;
        if (state.start != null && state.end != null) {
          try {
            editor.setSelectionRange(state.start, state.end);
          } catch (e) {}
        }
      }
    }

    function setEditorText(text, preserveView) {
      var viewState = null;
      if (preserveView) viewState = captureEditorViewState();
      if (markdownEditorCm) {
        markdownEditorCm.setValue(text || '');
        markdownEditorCm.refresh();
        if (preserveView) restoreEditorViewState(viewState);
        return;
      }
      const editor = document.getElementById('markdownEditor');
      if (!editor) return;
      editor.value = text || '';
      if (preserveView) restoreEditorViewState(viewState);
    }

    function resolveMarkdownHref(href) {
      if (!href) return null;
      if (href.startsWith('#') || href.startsWith('http://') || href.startsWith('https://') || href.startsWith('mailto:') || href.startsWith('data:')) return null;
      var clean = href.split('#')[0].split('?')[0];
      if (!clean || !clean.toLowerCase().endsWith('.md')) return null;
      if (clean.startsWith('/')) return clean.substring(1);
      if (clean.startsWith('doc/')) return clean;
      if (clean.startsWith('./')) clean = clean.substring(2);
      return 'doc/reference/' + clean;
    }

    function attachMarkdownLinkHandlers() {
      const content = document.getElementById('content');
      const links = content.querySelectorAll('a[href]');
      links.forEach(function(link) {
        const href = link.getAttribute('href');
        const mdUrl = resolveMarkdownHref(href);
        if (!mdUrl) return;
        link.addEventListener('click', function(e) {
          e.preventDefault();
          fetch(mdUrl).then(function(resp) {
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            return resp.text();
          }).then(function(text) {
            renderMarkdown(text);
          }).catch(function(err) {
            const c = document.getElementById('content');
            c.innerHTML = '<p>Failed to load markdown: ' + mdUrl + '</p>';
          });
        });
      });
    }
