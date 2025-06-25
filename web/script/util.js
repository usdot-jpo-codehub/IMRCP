
const toMicros = dbl => Math.round(dbl * 1000000);
const fromMicros = micros => micros / 1000000;
const distanceBetweenPoints = (p1, p2) => Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2));
const coordToMicroPoint = ([lng, lat]) => ({x: toMicros(lng), y: toMicros(lat)});

const findClosestCoord = (lngLat, coords) => {
  const lngLatPoint = {x: toMicros(lngLat.lng), y: toMicros(lngLat.lat)};

  const microCoords = coords.map(coordToMicroPoint);

  let closestDistance = distanceBetweenPoints(microCoords[0], lngLatPoint);
  let closestIndex = 0;
  for (let i = 1; i < coords.length; ++i)
  {
    const distance = distanceBetweenPoints(microCoords[i], lngLatPoint);
    if (distance < closestDistance)
    {
      closestIndex = i;
      closestDistance = distance;
    }
  }
  return coords[closestIndex];
};

export {findClosestCoord};