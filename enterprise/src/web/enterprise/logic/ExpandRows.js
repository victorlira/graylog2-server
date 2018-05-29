import { flatten, setWith } from 'lodash';

const expandRows = (fieldNames, columnFieldNames, series, rows, expanded = []) => {
  if (!rows) {
    return [];
  }
  if (fieldNames.length === 0) {
    return [];
  }

  rows.forEach((row) => {
    const { values } = row;
    const result = {};

    row.key.forEach((key, idx) => {
      result[fieldNames[idx]] = key;
    });

    values.forEach(({ key, value }) => {
      const translatedKeys = flatten(key.map((k, idx) => (idx < key.length - 1 && columnFieldNames[idx] ? [columnFieldNames[idx], k] : k)));
      setWith(result, translatedKeys, value, Object);
    });
    expanded.push(result);
  });
  return expanded;
};

export default expandRows;
