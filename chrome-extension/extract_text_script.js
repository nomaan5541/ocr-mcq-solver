/**
 * Smart Vision Solver - Precision MCQ Extractor
 * Isolates the active MCQ question and options (A, B, C, D)
 * and strips out search queries, prompt bubbles, and page chrome.
 */

(function() {
  // 1. If user highlighted any text with mouse, use it directly
  const selection = window.getSelection() ? window.getSelection().toString().trim() : "";
  if (selection && selection.length > 8) {
    return { text: selection, method: "user_highlight" };
  }

  // 2. Extract entire body innerText into clean lines
  const rawText = (document.body ? document.body.innerText : "") || "";
  const lines = rawText.split('\n').map(l => l.trim()).filter(Boolean);

  // 3. Find lines that start with option markers: A), A., (A), [A], Option A
  const optRegex = /^(?:[A-D][\)\.:]|(?:\([A-D]\))|(?:\[[A-D]\])|(?:Option\s+[A-D][:.]?))\s*/i;
  const optIndices = [];

  lines.forEach((line, idx) => {
    if (optRegex.test(line)) {
      optIndices.push(idx);
    }
  });

  // Check if we found at least 2 options
  if (optIndices.length >= 2) {
    let firstOpt = optIndices[0];
    let lastOpt = optIndices[optIndices.length - 1];

    if (lastOpt + 1 < lines.length && /^(?:[A-D][\)\.:]|(?:\([A-D]\))|(?:\[[A-D]\])|(?:Option\s+[A-D][:.]?))\s*$/i.test(lines[lastOpt])) {
      lastOpt++;
    }

    // Look back up to 8 lines before the first option for the question sentence
    let qLines = [];
    for (let i = firstOpt - 1; i >= Math.max(0, firstOpt - 8); i--) {
      const l = lines[i];
      if (/^(researched|search|share|links|answer|images|sources|cited|follow-up|feedback|bookmark|report)/i.test(l)) {
        continue;
      }
      qLines.unshift(l);
      if (l.includes('?') || /(which|what|how|where|when|who|why|choose|select|identify|determine|find)\b/i.test(l)) {
        break;
      }
    }

    if (qLines.length === 0 && firstOpt > 0) {
      qLines = [lines[firstOpt - 1]];
    }

    const options = lines.slice(firstOpt, lastOpt + 1);
    const fullMcq = [...qLines, ...options].join('\n');

    return {
      text: fullMcq,
      method: "precision_line_mcq_detector"
    };
  }

  // 4. Fallback: Search for numeric options (1., 2., 3., 4.)
  const numRegex = /^(?:[1-4][\)\.:]|(?:\([1-4]\)))\s*/;
  const numIndices = [];
  lines.forEach((line, idx) => {
    if (numRegex.test(line)) numIndices.push(idx);
  });

  if (numIndices.length >= 2) {
    let firstOpt = numIndices[0];
    let lastOpt = numIndices[numIndices.length - 1];
    if (lastOpt + 1 < lines.length && /^(?:[1-4][\)\.:]|(?:\([1-4]\)))\s*$/.test(lines[lastOpt])) {
      lastOpt++;
    }
    let qLines = [];
    for (let i = firstOpt - 1; i >= Math.max(0, firstOpt - 8); i--) {
      const l = lines[i];
      if (/^(researched|search|share|links|answer|images|sources)/i.test(l)) continue;
      qLines.unshift(l);
      if (l.includes('?') || /(which|what|how|where|when|who|why|choose|select)\b/i.test(l)) break;
    }
    if (qLines.length === 0 && firstOpt > 0) qLines = [lines[firstOpt - 1]];
    const fullMcq = [...qLines, ...lines.slice(firstOpt, lastOpt + 1)].join('\n');
    return { text: fullMcq, method: "numeric_line_mcq_detector" };
  }

  // 5. Look for question sentence with question mark
  const qIndex = lines.findIndex(l => l.includes('?') && l.length > 15);
  if (qIndex !== -1) {
    return {
      text: lines.slice(Math.max(0, qIndex - 1), Math.min(lines.length, qIndex + 8)).join('\n'),
      method: "question_mark_detector"
    };
  }

  // 6. Final Fallback: Return viewport visible text
  return {
    text: lines.slice(0, 25).join('\n'),
    method: "viewport_fallback"
  };
})();
