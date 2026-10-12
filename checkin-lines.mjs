/** One readable line per check-in, for the prompts of send.mjs and chat.mjs. */
export function describeCheckIn(checkIn) {
  if (checkIn.kind === 'workout') return describeWorkout(checkIn);
  if (checkIn.kind === 'screen_time') return `- ${checkIn.date} screen time: ${hoursAndMinutes(checkIn.minutes)}${checkIn.note ? ` (${checkIn.note})` : ''}`;
  const details = [
    checkIn.minutes ? `${checkIn.minutes} min` : null,
    checkIn.area ? `on ${checkIn.area}` : null,
    checkIn.time ? `at ${checkIn.time}` : null,
    checkIn.place ? `in ${checkIn.place}` : null,
    checkIn.energy ? `energy ${checkIn.energy}/5` : null,
  ].filter(Boolean).join(', ');
  return `- ${checkIn.date} ${checkIn.kind}${details ? ` (${details})` : ''}${checkIn.note ? `: ${checkIn.note}` : ''}`;
}

function hoursAndMinutes(minutes) {
  return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
}

function describeWorkout(workout) {
  const setsByExercise = Map.groupBy(workout.sets ?? [], (set) => set.exerciseName);
  const exerciseParts = [...setsByExercise].map(([exerciseName, sets]) => {
    const setTexts = sets.map((set) => `${set.kg} kg × ${set.reps}`);
    return `${exerciseName}: ${setTexts.join(', ')}`;
  });
  const feeling = workout.feeling ? ` | how it felt: ${workout.feeling}` : '';
  const note = workout.note ? ` | their note: ${workout.note}` : '';
  return `- ${workout.date} gym, ${workout.programDayName ?? 'workout'}${workout.minutes ? `, ${workout.minutes} min` : ''}: ${exerciseParts.join('; ') || 'no sets logged'}${feeling}${note}`;
}

// One line per day instead of one per card, so the reviews do not drown the rest of the list.
export function summarizeCardReviews(cardCheckIns) {
  const reviewsByDate = Map.groupBy(cardCheckIns, (checkIn) => checkIn.date);
  return [...reviewsByDate].map(([date, reviews]) => {
    const forgotten = reviews.filter((review) => review.grade === 'again').length;
    return `- ${date} card: ${reviews.length} cards reviewed, ${forgotten} forgotten`;
  });
}
