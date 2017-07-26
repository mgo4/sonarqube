/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
// @flow
import * as utils from '../utils';

const MEASURES = [
  {
    metric: {
      key: 'lines_to_cover',
      type: 'INT',
      name: 'Lines to Cover',
      domain: 'Coverage'
    },
    value: '431',
    periods: [{ index: 1, value: '70' }],
    leak: '70'
  },
  {
    metric: {
      key: 'coverage',
      type: 'PERCENT',
      name: 'Coverage',
      domain: 'Coverage'
    },
    value: '99.3',
    periods: [{ index: 1, value: '0.0999999999999943' }],
    leak: '0.0999999999999943'
  },
  {
    metric: {
      key: 'duplicated_lines_density',
      type: 'PERCENT',
      name: 'Duplicated Lines (%)',
      domain: 'Duplications'
    },
    value: '3.2',
    periods: [{ index: 1, value: '0.0' }],
    leak: '0.0'
  }
];

describe('filterMeasures', () => {
  it('should exclude banned measures', () => {
    expect(
      utils.filterMeasures([
        { metric: { key: 'bugs', name: 'Bugs', type: 'INT' } },
        { metric: { key: 'critical_violations', name: 'Critical Violations', type: 'INT' } }
      ])
    ).toHaveLength(1);
  });
});

describe('sortMeasures', () => {
  it('should sort based on the config', () => {
    expect(
      utils.sortMeasures('Reliability', [
        { metric: { key: 'reliability_remediation_effort', name: 'new_bugs', type: 'INT' } },
        { metric: { key: 'new_reliability_remediation_effort', name: 'bugs', type: 'INT' } },
        { metric: { key: 'new_bugs', name: 'new_bugs', type: 'INT' } },
        { metric: { key: 'bugs', name: 'bugs', type: 'INT' } }
      ])
    ).toMatchSnapshot();
  });
});

describe('groupByDomains', () => {
  it('should correctly group by domains', () => {
    expect(utils.groupByDomains(MEASURES)).toMatchSnapshot();
  });
});

describe('parseQuery', () => {
  it('should correctly parse the url query', () => {
    expect(utils.parseQuery({})).toEqual({ metric: '', view: utils.DEFAULT_VIEW });
    expect(utils.parseQuery({ metric: 'foo', view: 'tree' })).toEqual({
      metric: 'foo',
      view: 'tree'
    });
  });
});

describe('serializeQuery', () => {
  it('should correctly serialize the query', () => {
    expect(utils.serializeQuery({ metric: '', view: 'list' })).toEqual({});
    expect(utils.serializeQuery({ metric: 'foo', view: 'tree' })).toEqual({
      metric: 'foo',
      view: 'tree'
    });
  });
});