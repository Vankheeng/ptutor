// Câu hỏi gợi ý theo vai trò và trang đang mở (docs/support-chatbot/07-api.md mục 7.5).
export function SuggestionChips({ suggestions, onPick }: { suggestions: string[]; onPick: (text: string) => void }) {
  if (suggestions.length === 0) return null;
  return (
    <div className="assistant-suggestions">
      {suggestions.map((suggestion) => (
        <button key={suggestion} type="button" onClick={() => onPick(suggestion)}>
          {suggestion}
        </button>
      ))}
    </div>
  );
}
